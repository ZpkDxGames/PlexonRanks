package com.zpkdxgames.plexonranks.command;

import com.zpkdxgames.plexonranks.config.ConfigManager;
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
    private final RankListMenu menu;

    public RankCommand(ConfigManager configs, RankService ranks, RenderService render,
                       MessageService messages, RankListMenu menu) {
        this.configs = configs;
        this.ranks = ranks;
        this.render = render;
        this.messages = messages;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "generic.players-only");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("menu")) {
            menu.open(player, 1);
            return true;
        }
        show(player);
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
        List<String> lines = render.expand(messages.lines("rank.lines"), placeholders,
                render.requirementLines(progress), render.rewardLines(next.get()));
        for (String line : lines) player.sendMessage(configs.formatter().component(line, placeholders));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("info", "menu").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase())).toList() : List.of();
    }
}

