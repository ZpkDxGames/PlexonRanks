package com.zpkdxgames.plexonranks.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaceholderHook {
    private final boolean connected;

    public PlaceholderHook(JavaPlugin plugin, boolean enabled) {
        this.connected = enabled && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean connected() {
        return connected;
    }

    public String apply(Player player, String value) {
        return connected ? PlaceholderAPI.setPlaceholders(player, value) : value;
    }
}

