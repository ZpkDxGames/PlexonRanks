package com.zpkdxgames.plexonranks.event;

import com.zpkdxgames.plexonranks.model.Rank;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class PlexonRankChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Rank from;
    private final Rank to;
    private final RankChangeCause cause;

    public PlexonRankChangeEvent(Player player, Rank from, Rank to, RankChangeCause cause) {
        super(player);
        this.from = from;
        this.to = to;
        this.cause = cause;
    }

    public Rank from() {
        return from;
    }

    public Rank to() {
        return to;
    }

    public RankChangeCause cause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}

