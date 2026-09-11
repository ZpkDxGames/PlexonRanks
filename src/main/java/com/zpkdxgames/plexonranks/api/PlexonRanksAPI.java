package com.zpkdxgames.plexonranks.api;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankHistoryEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlexonRanksAPI {
    /** @deprecated Prefer immutable {@link #currentView(UUID)}. */
    @Deprecated(forRemoval = false)
    Optional<Rank> getRank(UUID playerId);

    /** @deprecated Prefer immutable {@link #nextView(UUID)}. */
    @Deprecated(forRemoval = false)
    Optional<Rank> getNextRank(UUID playerId);

    /** @deprecated Prefer immutable rank views from {@link #getRankViews()}. */
    @Deprecated(forRemoval = false)
    Optional<Rank> getRankById(String rankId);

    /** @deprecated Prefer immutable rank views from {@link #getRankViews()}. */
    @Deprecated(forRemoval = false)
    List<Rank> getRanks();

    boolean canRankup(UUID playerId);

    Optional<RankView> currentView(UUID playerId);

    Optional<RankView> nextView(UUID playerId);

    List<RankView> getRankViews();

    /** Requires an online, loaded player and primary-thread evaluation. */
    Optional<ProgressionView> progression(UUID playerId);

    CompletableFuture<List<RankHistoryEntry>> history(UUID playerId, int limit);
}
