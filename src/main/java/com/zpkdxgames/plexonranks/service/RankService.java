package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.database.DatabaseManager;
import com.zpkdxgames.plexonranks.model.PlayerRankData;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.reward.RewardEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class RankService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final DatabaseManager database;
    private final RewardEngine rewards;
    private final Map<UUID, PlayerRankData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerRankData>> loading = new ConcurrentHashMap<>();

    public RankService(JavaPlugin plugin, ConfigManager configs, DatabaseManager database, RewardEngine rewards) {
        this.plugin = plugin;
        this.configs = configs;
        this.database = database;
        this.rewards = rewards;
    }

    public CompletableFuture<PlayerRankData> load(UUID uuid) {
        PlayerRankData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return loading.computeIfAbsent(uuid, ignored -> database
                .loadOrCreate(uuid, configs.current().registry().defaultRank().id())
                .thenCompose(this::repairMissingRank)
                .whenComplete((data, error) -> {
                    loading.remove(uuid);
                    if (error == null) {
                        cache.put(uuid, data);
                    } else {
                        plugin.getLogger().severe("Could not load rank data for " + uuid + ": " + error.getMessage());
                    }
                }));
    }

    public boolean loaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public Optional<PlayerRankData> data(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public Optional<Rank> current(UUID uuid) {
        PlayerRankData data = cache.get(uuid);
        return data == null ? Optional.empty() : configs.current().registry().byId(data.rankId()).filter(Rank::enabled);
    }

    public Optional<Rank> next(Player player) {
        return current(player.getUniqueId()).flatMap(rank ->
                configs.current().registry().nextAccessible(rank, player::hasPermission));
    }

    public CompletableFuture<PlayerRankData> setRank(UUID uuid, Rank rank) {
        return database.forceSetRank(uuid, rank.id()).thenApply(data -> {
            cache.put(uuid, data);
            return data;
        });
    }

    public void acceptCommitted(UUID uuid, Rank rank) {
        PlayerRankData previous = cache.get(uuid);
        Instant firstJoined = previous == null ? Instant.now() : previous.firstJoinedAt();
        cache.put(uuid, new PlayerRankData(uuid, rank.id(), Instant.now(), firstJoined));
    }

    public CompletableFuture<Void> reconcile(Player player) {
        return reconcile(player.getUniqueId());
    }

    public CompletableFuture<Void> reconcile(UUID uuid) {
        return load(uuid).thenCompose(data -> {
            Rank current = configs.current().registry().byId(data.rankId()).orElse(configs.current().registry().defaultRank());
            boolean cumulative = configs.current().config().getBoolean("permissions.cumulative", true);
            return rewards.reconcile(uuid, current, configs.current().registry(), cumulative);
        });
    }

    public void unloadLater(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(uuid) == null && !loading.containsKey(uuid)) {
                cache.remove(uuid);
            }
        }, 40L);
    }

    public void repairCachedRanks() {
        String fallback = configs.current().config().getString("join.missing-rank-fallback", "FAIL");
        Rank defaultRank = configs.current().registry().defaultRank();
        for (PlayerRankData data : List.copyOf(cache.values())) {
            boolean available = configs.current().registry().byId(data.rankId()).filter(Rank::enabled).isPresent();
            if (available) continue;
            plugin.getLogger().warning("Cached player " + data.uuid() + " references unavailable rank '"
                    + data.rankId() + "'. Fallback: " + fallback);
            if ("FIRST".equalsIgnoreCase(fallback)) {
                database.forceSetRank(data.uuid(), defaultRank.id()).whenComplete((repaired, error) -> {
                    if (error == null) cache.put(data.uuid(), repaired);
                    else plugin.getLogger().severe("Could not repair cached rank for " + data.uuid() + ": " + error.getMessage());
                });
            }
        }
    }

    private CompletableFuture<PlayerRankData> repairMissingRank(PlayerRankData data) {
        if (configs.current().registry().byId(data.rankId()).filter(Rank::enabled).isPresent()) {
            return CompletableFuture.completedFuture(data);
        }
        String behavior = configs.current().config().getString("join.missing-rank-fallback", "FAIL");
        plugin.getLogger().warning("Player " + data.uuid() + " references missing rank '" + data.rankId() + "'. Fallback: " + behavior);
        if ("FIRST".equalsIgnoreCase(behavior)) {
            return database.forceSetRank(data.uuid(), configs.current().registry().defaultRank().id());
        }
        return CompletableFuture.failedFuture(new IllegalStateException("Configured rank no longer exists: " + data.rankId()));
    }
}
