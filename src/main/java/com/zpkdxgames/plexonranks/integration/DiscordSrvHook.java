package com.zpkdxgames.plexonranks.integration;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class DiscordSrvHook {
    private final JavaPlugin plugin;
    private final boolean connected;

    public DiscordSrvHook(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.connected = enabled && plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV");
    }

    public boolean connected() {
        return connected;
    }

    public CompletableFuture<Void> send(String gameChannel, String message) {
        if (!connected) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                Class<?> discordSrv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
                Object instance = discordSrv.getMethod("getPlugin").invoke(null);
                Object channel = discordSrv.getMethod("getDestinationTextChannelForGameChannelName", String.class)
                        .invoke(instance, gameChannel);
                if (channel == null) {
                    throw new IllegalStateException("DiscordSRV game channel was not found: " + gameChannel);
                }
                Method sendMessage = channel.getClass().getMethod("sendMessage", CharSequence.class);
                Object action = sendMessage.invoke(channel, message);
                action.getClass().getMethod("queue").invoke(action);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("DiscordSRV bridge failed", exception);
            }
        }).exceptionally(error -> {
            plugin.getLogger().warning("Discord rank announcement failed: " + error.getMessage());
            return null;
        });
    }
}

