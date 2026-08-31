package com.zpkdxgames.plexonranks.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class VaultHook {
    private final Economy economy;

    public VaultHook(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = registration == null ? null : registration.getProvider();
    }

    public boolean connected() {
        return economy != null;
    }

    public Optional<Economy> economy() {
        return Optional.ofNullable(economy);
    }
}

