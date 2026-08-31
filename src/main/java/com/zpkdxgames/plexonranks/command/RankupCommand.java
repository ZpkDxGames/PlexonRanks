package com.zpkdxgames.plexonranks.command;

import com.zpkdxgames.plexonranks.service.MessageService;
import com.zpkdxgames.plexonranks.service.RankupService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class RankupCommand implements CommandExecutor {
    private final RankupService rankup;
    private final MessageService messages;

    public RankupCommand(RankupService rankup, MessageService messages) {
        this.rankup = rankup;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "generic.players-only");
            return true;
        }
        rankup.attempt(player);
        return true;
    }
}

