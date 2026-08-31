package com.zpkdxgames.plexonranks.event;

import com.zpkdxgames.plexonranks.model.Rank;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class PlexonRankPreRankupEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Rank from;
    private final Rank to;
    private boolean cancelled;

    public PlexonRankPreRankupEvent(Player player, Rank from, Rank to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Rank from() {
        return from;
    }

    public Rank to() {
        return to;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}

