package com.zpkdxgames.plexonranks.model;

import java.util.List;

public record Rank(
        String id,
        int order,
        String tier,
        String nextRankId,
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
        id = id == null ? "" : id.trim();
        tier = tier == null || tier.isBlank() ? "Progression" : tier.trim();
        nextRankId = nextRankId == null ? "" : nextRankId.trim();
        bypassPermission = bypassPermission == null ? "" : bypassPermission.trim();
        requirements = List.copyOf(requirements);
        rewards = List.copyOf(rewards);
    }

    /** Compatibility constructor for 2.x integrations/tests that construct immutable rank models directly. */
    public Rank(
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
        this(id, order, "Progression", "", enabled, visible, defaultRank, bypassPermission,
                display, requirements, rewards, announce, menu);
    }
}
