package com.zpkdxgames.plexonranks;

import com.zpkdxgames.plexonranks.api.PlexonRanksAPI;
import com.zpkdxgames.plexonranks.api.PlexonRanksApiImpl;
import com.zpkdxgames.plexonranks.command.PlexonRanksCommand;
import com.zpkdxgames.plexonranks.command.RankCommand;
import com.zpkdxgames.plexonranks.command.RanksCommand;
import com.zpkdxgames.plexonranks.command.RankupCommand;
import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.config.RankConfigEditor;
import com.zpkdxgames.plexonranks.database.DatabaseManager;
import com.zpkdxgames.plexonranks.integration.DiscordSrvHook;
import com.zpkdxgames.plexonranks.integration.LuckPermsHook;
import com.zpkdxgames.plexonranks.integration.PlaceholderHook;
import com.zpkdxgames.plexonranks.integration.PlexonRanksExpansion;
import com.zpkdxgames.plexonranks.integration.VaultHook;
import com.zpkdxgames.plexonranks.listener.PlayerDataListener;
import com.zpkdxgames.plexonranks.menu.AdminRankMenu;
import com.zpkdxgames.plexonranks.menu.ChatInputManager;
import com.zpkdxgames.plexonranks.menu.RankListMenu;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.reward.RewardEngine;
import com.zpkdxgames.plexonranks.service.BackupService;
import com.zpkdxgames.plexonranks.service.MessageService;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RankupService;
import com.zpkdxgames.plexonranks.service.RenderService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PlexonRanksPlugin extends JavaPlugin {
    private ConfigManager configs;
    private DatabaseManager database;
    private RankListMenu rankListMenu;
    private PlexonRanksExpansion expansion;
    private PlexonRanksAPI api;

    @Override
    public void onEnable() {
        try {
            configs = new ConfigManager(this);
            configs.ensureDefaults();
            configs.loadInitial();

            database = new DatabaseManager(this, configs.current().config().getString("storage.sqlite.file", "database.db"));
            database.initialize();

            VaultHook vault = new VaultHook(this);
            LuckPermsHook luckPerms = new LuckPermsHook(this);
            if (!vault.connected()) throw new IllegalStateException("Vault is loaded, but no economy provider is registered.");
            if (!luckPerms.connected()) throw new IllegalStateException("LuckPerms API service is unavailable.");
            PlaceholderHook placeholders = new PlaceholderHook(this,
                    configs.current().config().getBoolean("integrations.placeholderapi", true));
            DiscordSrvHook discord = new DiscordSrvHook(this,
                    configs.current().config().getBoolean("integrations.discordsrv", false));

            RequirementEngine requirements = new RequirementEngine(vault, placeholders);
            RewardEngine rewards = new RewardEngine(this, vault, luckPerms, configs::formatter);
            MessageService messages = new MessageService(configs);
            RankService ranks = new RankService(this, configs, database, rewards);
            RenderService render = new RenderService(configs, requirements);
            RankupService rankup = new RankupService(this, configs, database, ranks, requirements, rewards, render, messages, discord);
            BackupService backups = new BackupService(this, configs, database);

            rankListMenu = new RankListMenu(this, configs, ranks, rankup, render);
            ChatInputManager chatInput = new ChatInputManager(this, messages);
            RankConfigEditor configEditor = new RankConfigEditor(this, configs);
            AdminRankMenu adminMenu = new AdminRankMenu(this, configs, configEditor, chatInput, messages);

            RankCommand rankCommand = new RankCommand(configs, ranks, render, messages, rankListMenu);
            command("rank").setExecutor(rankCommand);
            command("rank").setTabCompleter(rankCommand);
            command("ranks").setExecutor(new RanksCommand(rankListMenu, messages));
            command("rankup").setExecutor(new RankupCommand(rankup, messages));
            PlexonRanksCommand adminCommand = new PlexonRanksCommand(this, configs, ranks, messages, adminMenu, backups);
            command("plexonranks").setExecutor(adminCommand);
            command("plexonranks").setTabCompleter(adminCommand);

            Bukkit.getPluginManager().registerEvents(rankListMenu, this);
            Bukkit.getPluginManager().registerEvents(chatInput, this);
            Bukkit.getPluginManager().registerEvents(adminMenu, this);
            Bukkit.getPluginManager().registerEvents(new PlayerDataListener(this, configs, ranks), this);

            api = new PlexonRanksApiImpl(configs, ranks, requirements);
            Bukkit.getServicesManager().register(PlexonRanksAPI.class, api, this, ServicePriority.Normal);
            if (placeholders.connected()) {
                expansion = new PlexonRanksExpansion(this, configs, ranks, render);
                expansion.register();
            }
            Bukkit.getOnlinePlayers().forEach(player -> ranks.load(player.getUniqueId()));
            startupSummary(vault, luckPerms, placeholders, discord);
        } catch (Exception exception) {
            getLogger().severe("PlexonRanks could not start safely: " + exception.getMessage());
            exception.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (rankListMenu != null) rankListMenu.stop();
        if (expansion != null) expansion.unregister();
        Bukkit.getServicesManager().unregisterAll(this);
        if (database != null) database.close();
    }

    public PlexonRanksAPI api() {
        return Objects.requireNonNull(api, "PlexonRanks API is not available before enable completes");
    }

    private PluginCommand command(String name) {
        return Objects.requireNonNull(getCommand(name), "Command missing from plugin.yml: " + name);
    }

    private void startupSummary(VaultHook vault, LuckPermsHook luckPerms, PlaceholderHook placeholders, DiscordSrvHook discord) {
        getLogger().info("PlexonRanks " + getPluginMeta().getVersion());
        getLogger().info(" • Ranks: " + configs.current().registry().ordered().size());
        getLogger().info(" • Storage: SQLite");
        getLogger().info(" • Vault: " + status(vault.connected()));
        getLogger().info(" • LuckPerms: " + status(luckPerms.connected()));
        getLogger().info(" • PlaceholderAPI: " + status(placeholders.connected()));
        getLogger().info(" • DiscordSRV: " + (discord.connected() ? "CONNECTED" : "DISABLED"));
        getLogger().info(" • MiniMessage: ENABLED");
    }

    private static String status(boolean connected) {
        return connected ? "CONNECTED" : "UNAVAILABLE";
    }
}

