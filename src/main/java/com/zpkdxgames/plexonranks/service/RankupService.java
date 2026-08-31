package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.database.DatabaseManager;
import com.zpkdxgames.plexonranks.event.PlexonRankChangeEvent;
import com.zpkdxgames.plexonranks.event.PlexonRankPreRankupEvent;
import com.zpkdxgames.plexonranks.event.PlexonRankupEvent;
import com.zpkdxgames.plexonranks.event.RankChangeCause;
import com.zpkdxgames.plexonranks.integration.DiscordSrvHook;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.requirement.Consumption;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.reward.RewardEngine;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankupService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final DatabaseManager database;
    private final RankService ranks;
    private final RequirementEngine requirements;
    private final RewardEngine rewards;
    private final RenderService render;
    private final MessageService messages;
    private final DiscordSrvHook discord;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastAttempt = new ConcurrentHashMap<>();

    public RankupService(JavaPlugin plugin, ConfigManager configs, DatabaseManager database, RankService ranks,
                         RequirementEngine requirements, RewardEngine rewards, RenderService render,
                         MessageService messages, DiscordSrvHook discord) {
        this.plugin = plugin;
        this.configs = configs;
        this.database = database;
        this.ranks = ranks;
        this.requirements = requirements;
        this.rewards = rewards;
        this.render = render;
        this.messages = messages;
        this.discord = discord;
    }

    public void attempt(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> attempt(player));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!ranks.loaded(uuid)) {
            ranks.load(uuid);
            messages.send(player, "generic.data-loading");
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, configs.current().config().getLong("rankup.cooldown-ms", 750));
        if (now - lastAttempt.getOrDefault(uuid, 0L) < cooldown) {
            messages.send(player, "rankup.cooldown");
            return;
        }
        if (!processing.add(uuid)) {
            messages.send(player, "rankup.already-processing");
            return;
        }
        lastAttempt.put(uuid, now);

        Rank current = ranks.current(uuid).orElse(configs.current().registry().defaultRank());
        Optional<Rank> next = configs.current().registry().nextAccessible(current, player::hasPermission);
        if (next.isEmpty()) {
            messages.send(player, "rankup.max-rank");
            processing.remove(uuid);
            return;
        }
        Rank target = next.get();
        List<RequirementProgress> progress = requirements.evaluate(player, target.requirements());
        if (progress.stream().anyMatch(value -> !value.complete())) {
            messages.send(player, "rankup.requirements-not-met");
            playConfiguredSound(player, "sounds.denied");
            processing.remove(uuid);
            return;
        }

        PlexonRankPreRankupEvent preEvent = new PlexonRankPreRankupEvent(player, current, target);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            messages.send(player, "rankup.cancelled");
            processing.remove(uuid);
            return;
        }

        List<Consumption> consumed;
        try {
            consumed = requirements.consume(player, target.requirements());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Rank-up consumption failed for " + player.getName() + ": " + exception.getMessage());
            messages.send(player, "rankup.requirements-not-met");
            processing.remove(uuid);
            return;
        }

        String transactionId = UUID.randomUUID().toString();
        database.commitRankup(uuid, current.id(), target.id(), transactionId).whenComplete((committed, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || !Boolean.TRUE.equals(committed)) {
                        requirements.rollback(consumed);
                        plugin.getLogger().severe("Rank transaction " + transactionId + " failed for " + player.getName()
                                + ": " + (error == null ? "stale rank state" : rootMessage(error)));
                        messages.send(player, "generic.database-error");
                        processing.remove(uuid);
                        return;
                    }
                    ranks.acceptCommitted(uuid, target);
                    Bukkit.getPluginManager().callEvent(new PlexonRankChangeEvent(player, current, target, RankChangeCause.RANKUP));
                    Map<String, String> placeholders = render.placeholders(player, current, target, progress, RankState.NEXT);
                    rewards.execute(player, target, placeholders).whenComplete((ignored, rewardError) ->
                            Bukkit.getScheduler().runTask(plugin, () -> finish(player, current, target, transactionId,
                                    placeholders, rewardError)));
                }));
    }

    public boolean processing(UUID uuid) {
        return processing.contains(uuid);
    }

    private void finish(Player player, Rank from, Rank to, String transactionId,
                        Map<String, String> placeholders, Throwable rewardError) {
        try {
            if (rewardError != null) {
                plugin.getLogger().severe("Reward execution failed after saved rank transaction " + transactionId
                        + " for " + player.getName() + ": " + rootMessage(rewardError));
                database.markTransactionFailed(transactionId, "REWARD_FAILED");
                messages.send(player, "rankup.reward-error");
            } else {
                database.completeTransaction(transactionId);
            }
            feedback(player, to, placeholders);
            Bukkit.getPluginManager().callEvent(new PlexonRankupEvent(player, from, to, transactionId));
        } finally {
            processing.remove(player.getUniqueId());
        }
    }

    private void feedback(Player player, Rank rank, Map<String, String> placeholders) {
        if (configs.current().config().getBoolean("feedback.chat", true)) {
            messages.send(player, "rankup.success", placeholders);
        }
        if (configs.current().config().getBoolean("feedback.title", true)) {
            player.showTitle(Title.title(
                    messages.component("rankup.title", placeholders),
                    messages.component("rankup.subtitle", placeholders),
                    Title.Times.times(Duration.ofMillis(350), Duration.ofSeconds(3), Duration.ofMillis(600))
            ));
        }
        if (configs.current().config().getBoolean("feedback.sound", true)) {
            playConfiguredSound(player, "sounds.rankup");
        }
        if (rank.announce()
                && configs.current().config().getBoolean("broadcast.enabled", true)
                && configs.current().config().getBoolean("feedback.broadcast", true)) {
            Bukkit.getServer().sendMessage(messages.component("rankup.broadcast", placeholders));
        }
        if (discord.connected()) {
            String channel = configs.current().config().getString("discord.game-channel", "global");
            String message = TextFormatter.replaceRaw(configs.current().config().getString("discord.message", ""), placeholders);
            discord.send(channel, configs.formatter().plain(configs.formatter().component(message)));
        }
    }

    private void playConfiguredSound(Player player, String path) {
        String sound = configs.current().config().getString(path + ".sound", "");
        if (sound.isBlank()) {
            return;
        }
        float volume = (float) configs.current().config().getDouble(path + ".volume", 1.0);
        float pitch = (float) configs.current().config().getDouble(path + ".pitch", 1.0);
        try {
            player.playSound(player.getLocation(), sound.toLowerCase().contains(":") ? sound.toLowerCase() : "minecraft:" + sound.toLowerCase(), volume, pitch);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid configured sound " + sound + " at " + path);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}

