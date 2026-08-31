package com.zpkdxgames.plexonranks.api;

import com.zpkdxgames.plexonranks.model.Rank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlexonRanksAPI {
    Optional<Rank> getRank(UUID playerId);

    Optional<Rank> getNextRank(UUID playerId);

    Optional<Rank> getRankById(String rankId);

    List<Rank> getRanks();

    boolean canRankup(UUID playerId);
}

