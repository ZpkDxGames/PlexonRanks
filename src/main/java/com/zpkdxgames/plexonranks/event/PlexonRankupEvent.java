package com.zpkdxgames.plexonranks.event;

import com.zpkdxgames.plexonranks.model.Rank;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class PlexonRankupEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Rank from;
    private final Rank to;
    private final String transactionId;

    public PlexonRankupEvent(Player player, Rank from, Rank to, String transactionId) {
        super(player);
        this.from = from;
        this.to = to;
        this.transactionId = transactionId;
    }

    public Rank from() {
        return from;
    }

    public Rank to() {
        return to;
    }

    public String transactionId() {
        return transactionId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}

