package com.zpkdxgames.plexonranks.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ProgressionView(
        UUID playerId,
        RankView current,
        Optional<RankView> next,
        List<RequirementView> requirements,
        double progress,
        boolean canRankup,
        boolean maximumRank
) {
    public ProgressionView {
        next = next == null ? Optional.empty() : next;
        requirements = List.copyOf(requirements);
        progress = Math.max(0.0, Math.min(1.0, progress));
    }
}
