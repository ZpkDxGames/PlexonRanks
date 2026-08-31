package com.zpkdxgames.plexonranks.command;

import com.zpkdxgames.plexonranks.menu.RankListMenu;
import com.zpkdxgames.plexonranks.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class RanksCommand implements CommandExecutor {
    private final RankListMenu menu;
    private final MessageService messages;

    public RanksCommand(RankListMenu menu, MessageService messages) {
        this.menu = menu;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "generic.players-only");
            return true;
        }
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException exception) {
                messages.send(player, "generic.invalid-number");
                return true;
            }
        }
        menu.open(player, page);
        return true;
    }
}

