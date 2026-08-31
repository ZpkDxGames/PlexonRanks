package com.zpkdxgames.plexonranks.model;

import java.util.List;

public record Rank(
        String id,
        int order,
        boolean enabled,
        boolean visible,
        boolean defaultRank,
        String bypassPermission,
        RankDisplay display,
        List<RequirementDefinition> requirements,
        List<RewardDefinition> rewards,
        boolean announce,
        RankMenu menu
) {
    public Rank {
        requirements = List.copyOf(requirements);
        rewards = List.copyOf(rewards);
    }
}

