package com.zpkdxgames.plexonranks.command;

import com.zpkdxgames.plexonranks.api.PlexonRanksAPI;
import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.config.ReloadResult;
import com.zpkdxgames.plexonranks.database.DatabaseManager;
import com.zpkdxgames.plexonranks.event.PlexonRankChangeEvent;
import com.zpkdxgames.plexonranks.event.PlexonRankupEvent;
import com.zpkdxgames.plexonranks.event.RankChangeCause;
import com.zpkdxgames.plexonranks.integration.DiscordSrvHook;
import com.zpkdxgames.plexonranks.integration.LuckPermsHook;
import com.zpkdxgames.plexonranks.integration.PlaceholderHook;
import com.zpkdxgames.plexonranks.integration.VaultHook;
import com.zpkdxgames.plexonranks.integration.core.CoreBridge;
import com.zpkdxgames.plexonranks.menu.AdminRankMenu;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import com.zpkdxgames.plexonranks.service.BackupService;
import com.zpkdxgames.plexonranks.service.MessageService;
import com.zpkdxgames.plexonranks.service.RankService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlexonRanksCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("help", "reload", "admin", "info", "diagnostics", "setrank",
            "resetrank", "promote", "demote", "sync", "validate", "backup");
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final MessageService messages;
    private final AdminRankMenu adminMenu;
    private final BackupService backups;
    private final DatabaseManager database;
    private final VaultHook vault;
    private final LuckPermsHook luckPerms;
    private final PlaceholderHook placeholders;
    private final DiscordSrvHook discord;
    private final CoreBridge core;
    private final Map<String, PendingReset> resets = new HashMap<>();

    public PlexonRanksCommand(JavaPlugin plugin, ConfigManager configs, RankService ranks, MessageService messages,
                              AdminRankMenu adminMenu, BackupService backups, DatabaseManager database,
                              VaultHook vault, LuckPermsHook luckPerms, PlaceholderHook placeholders,
                              DiscordSrvHook discord, CoreBridge core) {
        this.plugin = plugin;
        this.configs = configs;
        this.ranks = ranks;
        this.messages = messages;
        this.adminMenu = adminMenu;
        this.backups = backups;
        this.database = database;
        this.vault = vault;
        this.luckPerms = luckPerms;
        this.placeholders = placeholders;
        this.discord = discord;
        this.core = core;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!permitted(sender, sub)) return true;
        switch (sub) {
            case "help" -> messages.sendLines(sender, "admin.help", Map.of());
            case "reload" -> reload(sender);
            case "validate" -> validation(sender, configs.validateCandidate());
            case "admin" -> {
                if (sender instanceof Player player) adminMenu.openList(player, 1); else messages.send(sender, "generic.players-only");
            }
            case "info" -> info(sender, args);
            case "diagnostics" -> diagnostics(sender);
            case "setrank" -> setRank(sender, args, RankChangeCause.ADMIN_SET);
            case "promote" -> shift(sender, args, true);
            case "demote" -> shift(sender, args, false);
            case "resetrank" -> reset(sender, args);
            case "sync" -> sync(sender, args);
            case "backup" -> backup(sender);
            default -> messages.sendLines(sender, "admin.help", Map.of());
        }
        return true;
    }

    private void reload(CommandSender sender) {
        ReloadResult result = configs.reload();
        if (!result.success()) {
            messages.send(sender, "admin.reload-failed");
            printIssues(sender, result);
            return;
        }
        messages.send(sender, "admin.reload-success", Map.of("ranks", String.valueOf(configs.current().registry().ordered().size())));
        if (result.restartRequired()) messages.send(sender, "admin.storage-restart-required");
        result.issues().stream().filter(issue -> issue.severity() == ValidationIssue.Severity.WARNING)
                .forEach(issue -> sender.sendMessage(messages.component("admin.validation-warning",
                        Map.of("source", issue.source(), "message", issue.message()))));
    }

    private void validation(CommandSender sender, ReloadResult result) {
        if (result.success() && result.issues().stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.WARNING)) {
            messages.send(sender, "admin.validate-success");
            return;
        }
        printIssues(sender, result);
    }

    private void printIssues(CommandSender sender, ReloadResult result) {
        long errors = result.issues().stream().filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).count();
        long warnings = result.issues().size() - errors;
        messages.send(sender, "admin.validate-header", Map.of("errors", String.valueOf(errors), "warnings", String.valueOf(warnings)));
        result.issues().stream().limit(20).forEach(issue -> sender.sendMessage(messages.component(
                issue.severity() == ValidationIssue.Severity.ERROR ? "admin.validation-error" : "admin.validation-warning",
                Map.of("source", issue.source(), "message", issue.message()))));
    }

    private void diagnostics(CommandSender sender) {
        long cachedOnline = Bukkit.getOnlinePlayers().stream()
                .filter(player -> ranks.loaded(player.getUniqueId()))
                .count();
        String economyProvider = vault.economy().map(provider -> provider.getName()).orElse("-");
        boolean apiRegistered = Bukkit.getServicesManager().getRegistration(PlexonRanksAPI.class) != null;

        sender.sendMessage("PlexonRanks Diagnostics");
        sender.sendMessage("  Version ............ " + plugin.getPluginMeta().getVersion());
        sender.sendMessage("  Paper .............. " + Bukkit.getVersion());
        sender.sendMessage("  Java ............... " + Runtime.version());
        sender.sendMessage("  Database ........... READY (" + database.databasePath().getFileName() + ")");
        sender.sendMessage("  Loaded ranks ....... " + configs.current().registry().ordered().size());
        sender.sendMessage("  Online cached ...... " + cachedOnline + "/" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("  Vault .............. " + status(vault.connected()));
        sender.sendMessage("  Economy provider ... " + economyProvider);
        sender.sendMessage("  LuckPerms .......... " + status(luckPerms.connected()));
        sender.sendMessage("  PlaceholderAPI ..... " + status(placeholders.connected()));
        sender.sendMessage("  DiscordSRV ......... " + (discord.connected() ? "CONNECTED" : "DISABLED/UNAVAILABLE"));
        sender.sendMessage("  PlexonCore ......... " + (core.installed() ? core.pluginVersion() : "NOT INSTALLED"));
        sender.sendMessage("  Core API ........... " + core.apiVersion() + " (supported " + CoreBridge.SUPPORTED_API_RANGE + ")");
        sender.sendMessage("  Core mode .......... " + core.mode());
        sender.sendMessage("  Core module ........ " + core.registrationState());
        sender.sendMessage("  Core detail ........ " + core.detail());
        sender.sendMessage("  Public API ......... " + (apiRegistered ? "REGISTERED" : "MISSING"));
        sender.sendMessage("  Rankup contract .... " + PlexonRankupEvent.class.getName() + " / transactionId");
    }

    private void info(CommandSender sender, String[] args) {
        OfflinePlayer target = target(sender, args, 1);
        if (target == null) return;
        ranks.load(target.getUniqueId()).whenComplete((data, error) -> sync(() -> {
            if (error != null) {
                messages.send(sender, "generic.database-error");
                return;
            }
            Rank rank = configs.current().registry().byId(data.rankId()).orElse(configs.current().registry().defaultRank());
            messages.sendLines(sender, "admin.info-lines", Map.of(
                    "player", safeName(target),
                    "rank_name", rank.display().name(),
                    "rank_id", rank.id(),
                    "rank_order", String.valueOf(rank.order())));
        }));
    }

    private void setRank(CommandSender sender, String[] args, RankChangeCause cause) {
        if (args.length < 3) {
            messages.send(sender, "admin.setrank-usage");
            return;
        }
        OfflinePlayer target = target(sender, args, 1);
        if (target == null) return;
        Optional<Rank> destination = configs.current().registry().find(args[2]);
        if (destination.isEmpty()) {
            messages.send(sender, "admin.unknown-rank", Map.of("rank", args[2]));
            return;
        }
        boolean grantPersistent = java.util.Arrays.stream(args).anyMatch("--grant-persistent"::equalsIgnoreCase);
        change(sender, target, destination.get(), cause, grantPersistent);
    }

    private void shift(CommandSender sender, String[] args, boolean promote) {
        if (args.length < 2) {
            messages.send(sender, "admin.shift-usage", Map.of("direction", promote ? "promote" : "demote"));
            return;
        }
        OfflinePlayer target = target(sender, args, 1);
        if (target == null) return;
        int amount = 1;
        if (args.length > 2) {
            try { amount = Math.max(1, Integer.parseInt(args[2])); }
            catch (NumberFormatException exception) { messages.send(sender, "generic.invalid-number"); return; }
        }
        int delta = promote ? amount : -amount;
        ranks.load(target.getUniqueId()).whenComplete((data, error) -> sync(() -> {
            if (error != null) { messages.send(sender, "generic.database-error"); return; }
            Rank current = configs.current().registry().byId(data.rankId()).orElse(configs.current().registry().defaultRank());
            Rank destination = configs.current().registry().shift(current, delta).orElse(current);
            change(sender, target, destination, promote ? RankChangeCause.ADMIN_PROMOTE : RankChangeCause.ADMIN_DEMOTE, true);
        }));
    }

    private void reset(CommandSender sender, String[] args) {
        OfflinePlayer target = target(sender, args, 1);
        if (target == null) return;
        String key = senderKey(sender);
        PendingReset pending = resets.get(key);
        boolean confirmed = args.length > 2 && args[2].equalsIgnoreCase("confirm") && pending != null
                && pending.playerId().equals(target.getUniqueId()) && pending.expiresAt() >= System.currentTimeMillis();
        if (!confirmed) {
            resets.put(key, new PendingReset(target.getUniqueId(), System.currentTimeMillis() + 30_000L));
            messages.send(sender, "admin.reset-confirm", Map.of("player", safeName(target)));
            return;
        }
        resets.remove(key);
        Rank destination = configs.current().registry().defaultRank();
        change(sender, target, destination, RankChangeCause.ADMIN_RESET, true);
    }

    private void sync(CommandSender sender, String[] args) {
        OfflinePlayer target = target(sender, args, 1);
        if (target == null) return;
        ranks.reconcile(target.getUniqueId()).whenComplete((ignored, error) -> sync(() -> {
            if (error == null) messages.send(sender, "admin.sync-success", Map.of("player", safeName(target)));
            else messages.send(sender, "generic.database-error");
        }));
    }

    private void backup(CommandSender sender) {
        backups.create().whenComplete((path, error) -> sync(() -> {
            if (error == null) messages.send(sender, "admin.backup-success", Map.of("file", path.getFileName().toString()));
            else {
                plugin.getLogger().warning("Backup failed: " + error.getMessage());
                messages.send(sender, "admin.backup-failed");
            }
        }));
    }

    private void change(CommandSender sender, OfflinePlayer target, Rank destination, RankChangeCause cause, boolean grantPersistent) {
        ranks.load(target.getUniqueId()).whenComplete((data, loadError) -> sync(() -> {
            if (loadError != null) { messages.send(sender, "generic.database-error"); return; }
            Rank old = configs.current().registry().byId(data.rankId()).orElse(configs.current().registry().defaultRank());
            ranks.setRank(target.getUniqueId(), destination).whenComplete((updated, saveError) -> sync(() -> {
                if (saveError != null) { messages.send(sender, "generic.database-error"); return; }
                Player online = target.getPlayer();
                if (online != null) Bukkit.getPluginManager().callEvent(new PlexonRankChangeEvent(online, old, destination, cause));
                plugin.getLogger().info("[ADMIN] " + sender.getName() + " set " + safeName(target) + " from " + old.id() + " to " + destination.id() + ".");
                messages.send(sender, "admin.setrank-success", Map.of("player", safeName(target), "old_rank", old.display().name(), "new_rank", destination.display().name()));
                if (grantPersistent) ranks.reconcile(target.getUniqueId());
            }));
        }));
    }

    private OfflinePlayer target(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            messages.send(sender, "admin.player-required");
            return null;
        }
        Player online = Bukkit.getPlayerExact(args[index]);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[index]);
        if (!offline.hasPlayedBefore()) {
            messages.send(sender, "generic.player-not-found", Map.of("player", args[index]));
            return null;
        }
        return offline;
    }

    private boolean permitted(CommandSender sender, String sub) {
        String node = switch (sub) {
            case "reload" -> "plexonranks.admin.reload";
            case "admin" -> "plexonranks.admin.editor";
            case "info" -> "plexonranks.admin.info";
            case "diagnostics" -> "plexonranks.admin.diagnostics";
            case "setrank" -> "plexonranks.admin.setrank";
            case "resetrank" -> "plexonranks.admin.reset";
            case "promote" -> "plexonranks.admin.promote";
            case "demote" -> "plexonranks.admin.demote";
            case "sync" -> "plexonranks.admin.sync";
            case "validate" -> "plexonranks.admin.validate";
            case "backup" -> "plexonranks.admin.backup";
            default -> "plexonranks.admin";
        };
        if (sender.hasPermission(node)) return true;
        messages.send(sender, "generic.no-permission");
        return false;
    }

    private void sync(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private static String senderKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console:" + sender.getName();
    }

    private static String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static String status(boolean connected) {
        return connected ? "CONNECTED" : "UNAVAILABLE";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);
        if (args.length == 2 && List.of("info", "setrank", "resetrank", "promote", "demote", "sync").contains(args[0].toLowerCase())) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setrank")) return filter(configs.current().registry().ids(), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("resetrank")) return filter(List.of("confirm"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }

    private record PendingReset(UUID playerId, long expiresAt) {
    }
}
