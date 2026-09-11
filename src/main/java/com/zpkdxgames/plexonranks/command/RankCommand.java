package com.zpkdxgames.plexonranks.command;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.menu.RankDashboardMenu;
import com.zpkdxgames.plexonranks.menu.RankListMenu;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.service.MessageService;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RenderService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RankCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configs;
    private final RankService ranks;
    private final RenderService render;
    private final MessageService messages;
    private final RankListMenu pathMenu;
    private final RankDashboardMenu dashboard;

    public RankCommand(ConfigManager configs, RankService ranks, RenderService render,
                       MessageService messages, RankListMenu pathMenu, RankDashboardMenu dashboard) {
        this.configs = configs;
        this.ranks = ranks;
        this.render = render;
        this.messages = messages;
        this.pathMenu = pathMenu;
        this.dashboard = dashboard;
    }

    /** 2.x compatibility constructor used by integrations/tests. */
    public RankCommand(ConfigManager configs, RankService ranks, RenderService render,
                       MessageService messages, RankListMenu pathMenu) {
        this(configs, ranks, render, messages, pathMenu, null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "generic.players-only");
            return true;
        }
        if (args.length == 0) {
            if (dashboard != null) dashboard.open(player); else show(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "info" -> show(player);
            case "menu" -> { if (dashboard != null) dashboard.open(player); else pathMenu.open(player, 1); }
            case "path" -> pathMenu.open(player, 1);
            case "requirements" -> { if (dashboard != null) dashboard.openRequirements(player); else show(player); }
            case "rewards" -> { if (dashboard != null) dashboard.openRewards(player); else show(player); }
            case "history" -> { if (dashboard != null) dashboard.openHistory(player); else show(player); }
            case "help" -> { if (dashboard != null) dashboard.openHelp(player); else show(player); }
            default -> { if (dashboard != null) dashboard.open(player); else show(player); }
        }
        return true;
    }

    public void show(Player player) {
        if (!ranks.loaded(player.getUniqueId())) {
            ranks.load(player.getUniqueId());
            messages.send(player, "generic.data-loading");
            return;
        }
        Rank current = ranks.current(player.getUniqueId()).orElse(configs.current().registry().defaultRank());
        Optional<Rank> next = configs.current().registry().nextAccessible(current, player::hasPermission);
        if (next.isEmpty()) {
            messages.sendLines(player, "rank.max-lines", render.placeholders(player, current, Optional.empty()));
            return;
        }
        List<RequirementProgress> progress = render.progress(player, next.get());
        Map<String, String> placeholders = render.placeholders(player, current, next.get(), progress, RankState.NEXT);
        List<String> lines = render.expand(messages.lines("rank.lines"), next.get(), RankState.NEXT,
                render.requirementLines(progress), render.rewardLines(next.get()));
        for (String line : lines) player.sendMessage(configs.formatter().component(line, placeholders));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("info", "menu", "path", "requirements", "rewards", "history", "help").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase())).toList() : List.of();
    }
}
