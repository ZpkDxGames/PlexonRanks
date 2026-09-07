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
import com.zpkdxgames.plexonranks.integration.core.CoreBridge;
import com.zpkdxgames.plexonranks.integration.core.CoreBridgeFactory;
import com.zpkdxgames.plexonranks.listener.PlayerDataListener;
import com.zpkdxgames.plexonranks.menu.AdminRankMenu;
import com.zpkdxgames.plexonranks.menu.ChatInputManager;
import com.zpkdxgames.plexonranks.menu.RankListMenu;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.reward.RewardEngine;
import com.zpkdxgames.plexonranks.service.BackupService;
import com.zpkdxgames.plexonranks.service.MessageService;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RankupService;
import com.zpkdxgames.plexonranks.service.RenderService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonRanksPlugin extends JavaPlugin {
    private ConfigManager configs;
    private DatabaseManager database;
    private RankService ranks;
    private RankListMenu rankListMenu;
    private PlexonRanksExpansion expansion;
    private PlexonRanksAPI api;
    private VaultHook vault;
    private LuckPermsHook luckPerms;
    private PlaceholderHook placeholders;
    private DiscordSrvHook discord;
    private CoreBridge core;

    @Override
    public void onEnable() {
        try {
            core = CoreBridgeFactory.resolve(this);
            core.registerStarting();

            configs = new ConfigManager(this);
            configs.ensureDefaults();
            configs.loadInitial();

            database = new DatabaseManager(this, configs.current().config().getString("storage.sqlite.file", "database.db"));
            database.initialize();

            vault = new VaultHook(this);
            luckPerms = new LuckPermsHook(this);
            auditCoreProviderHint("VAULT", vault.connected());
            auditCoreProviderHint("LUCKPERMS", luckPerms.connected());
            if (!vault.connected()) throw new IllegalStateException("Vault is loaded, but no economy provider is registered.");
            if (!luckPerms.connected()) throw new IllegalStateException("LuckPerms API service is unavailable.");

            boolean placeholderEnabled = configs.current().config().getBoolean("integrations.placeholderapi", true);
            placeholders = new PlaceholderHook(this, placeholderEnabled);
            if (placeholderEnabled) {
                auditCoreProviderHint("PLACEHOLDERAPI", placeholders.connected());
            }
            boolean placeholderRequirements = configs.current().registry().all().stream()
                    .flatMap(rank -> rank.requirements().stream())
                    .anyMatch(requirement -> requirement.type() == RequirementType.PLACEHOLDER);
            if (placeholderRequirements && !placeholders.connected()) {
                throw new IllegalStateException("PlaceholderAPI is required because PLACEHOLDER requirements are configured.");
            }

            boolean discordEnabled = configs.current().config().getBoolean("integrations.discordsrv", false);
            discord = new DiscordSrvHook(this, discordEnabled);

            RequirementEngine requirements = new RequirementEngine(vault, placeholders);
            RewardEngine rewards = new RewardEngine(this, vault, luckPerms, configs::formatter);
            MessageService messages = new MessageService(configs);
            ranks = new RankService(this, configs, database, rewards);
            configs.onReload(() -> {
                ranks.repairCachedRanks();
                publishCoreHealth();
            });
            RenderService render = new RenderService(configs, requirements);
            RankupService rankup = new RankupService(this, configs, database, ranks, requirements, rewards, render, messages, discord);
            BackupService backups = new BackupService(this, configs, database);

            rankListMenu = new RankListMenu(this, configs, ranks, rankup, render);
            ChatInputManager chatInput = new ChatInputManager(this, messages);
            RankConfigEditor configEditor = new RankConfigEditor(this, configs);
            AdminRankMenu adminMenu = new AdminRankMenu(this, configs, configEditor, chatInput, messages, render);

            RankCommand rankCommand = new RankCommand(configs, ranks, render, messages, rankListMenu);
            command("rank").setExecutor(rankCommand);
            command("rank").setTabCompleter(rankCommand);
            command("ranks").setExecutor(new RanksCommand(rankListMenu, messages));
            command("rankup").setExecutor(new RankupCommand(rankup, messages));
            PlexonRanksCommand adminCommand = new PlexonRanksCommand(
                    this, configs, ranks, messages, adminMenu, backups, database,
                    vault, luckPerms, placeholders, discord, core);
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
                if (!expansion.register()) {
                    getLogger().warning("PlaceholderAPI was present but the plexonranks expansion did not register.");
                    expansion = null;
                }
            }

            Bukkit.getOnlinePlayers().forEach(player -> ranks.load(player.getUniqueId()));
            publishCoreHealth();
            startupSummary();
        } catch (Exception exception) {
            if (core != null) {
                core.markFailed("Rank startup failed: " + exception.getClass().getSimpleName());
            }
            getLogger().log(Level.SEVERE, "PlexonRanks could not start safely; disabling without partial operation", exception);
            shutdown();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        shutdown();
    }

    private void shutdown() {
        if (rankListMenu != null) {
            rankListMenu.stop();
            rankListMenu = null;
        }
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        Bukkit.getServicesManager().unregisterAll(this);
        api = null;
        if (database != null) {
            database.close();
            database = null;
        }
        if (core != null) {
            core.unregister();
            core = null;
        }
    }

    public PlexonRanksAPI api() {
        return Objects.requireNonNull(api, "PlexonRanks API is not available before enable completes");
    }

    private PluginCommand command(String name) {
        return Objects.requireNonNull(getCommand(name), "Command missing from plugin.yml: " + name);
    }

    private void auditCoreProviderHint(String integrationId, boolean directlyAvailable) {
        if (core == null || !core.available()) return;
        CoreBridge.ProviderHint hint = core.providerHint(integrationId);
        boolean contradictsDirectState = (hint == CoreBridge.ProviderHint.PRESENT && !directlyAvailable)
                || (hint == CoreBridge.ProviderHint.MISSING && directlyAvailable);
        if (contradictsDirectState) {
            getLogger().warning("PlexonCore provider hint for " + integrationId + " is " + hint
                    + " while direct PlexonRanks API validation reports "
                    + (directlyAvailable ? "available" : "unavailable")
                    + "; direct provider validation remains authoritative.");
        }
    }

    private void publishCoreHealth() {
        if (core == null) return;

        List<String> degraded = new ArrayList<>();
        if (configs.current().config().getBoolean("integrations.placeholderapi", true) && !placeholders.connected()) {
            degraded.add("PlaceholderAPI enabled but unavailable");
        }
        if (configs.current().config().getBoolean("integrations.discordsrv", false) && !discord.connected()) {
            degraded.add("DiscordSRV enabled but unavailable");
        }

        String readyDetail = "Rank engine ready; " + configs.current().registry().ordered().size() + " ranks loaded";
        if (degraded.isEmpty()) {
            core.markReady(readyDetail);
        } else {
            core.markDegraded(readyDetail + "; " + String.join(", ", degraded));
        }
    }

    private void startupSummary() {
        getLogger().info("PlexonRanks " + getPluginMeta().getVersion());
        getLogger().info(" • Ranks: " + configs.current().registry().ordered().size());
        getLogger().info(" • Storage: SQLite");
        getLogger().info(" • Vault: " + status(vault.connected()));
        getLogger().info(" • LuckPerms: " + status(luckPerms.connected()));
        getLogger().info(" • PlaceholderAPI: " + status(placeholders.connected()));
        getLogger().info(" • DiscordSRV: " + (discord.connected() ? "CONNECTED" : "DISABLED"));
        getLogger().info(" • PlexonCore: " + (core.installed()
                ? core.mode() + " API " + core.apiVersion()
                : "STANDALONE"));
        getLogger().info(" • Module: " + core.registrationState());
        getLogger().info(" • MiniMessage: " + (configs.current().config().getBoolean("formatting.minimessage", true)
                ? "ENABLED" : "DISABLED"));
    }

    private static String status(boolean connected) {
        return connected ? "CONNECTED" : "UNAVAILABLE";
    }
}
