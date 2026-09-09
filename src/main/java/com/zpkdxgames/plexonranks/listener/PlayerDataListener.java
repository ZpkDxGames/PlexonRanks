package com.zpkdxgames.plexonranks.listener;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RankupService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

public final class PlayerDataListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final RankupService rankup;
    private final Consumer<UUID> displayInvalidator;

    public PlayerDataListener(JavaPlugin plugin, ConfigManager configs, RankService ranks) {
        this(plugin, configs, ranks, null, ignored -> { });
    }

    public PlayerDataListener(JavaPlugin plugin, ConfigManager configs, RankService ranks,
                              RankupService rankup, Consumer<UUID> displayInvalidator) {
        this.plugin = plugin;
        this.configs = configs;
        this.ranks = ranks;
        this.rankup = rankup;
        this.displayInvalidator = displayInvalidator;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ranks.load(player.getUniqueId()).thenCompose(data -> {
            if (configs.current().config().getBoolean("permissions.reconcile-on-join", true)) {
                return ranks.reconcile(player);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }).exceptionally(error -> {
            plugin.getLogger().warning("Player rank initialization failed for " + player.getName() + ": " + error.getMessage());
            return null;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (rankup != null) {
            rankup.clearPlayerState(uuid);
        }
        displayInvalidator.accept(uuid);
        ranks.unloadLater(event.getPlayer());
    }
}
