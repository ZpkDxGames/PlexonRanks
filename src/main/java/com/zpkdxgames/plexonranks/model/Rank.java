package com.zpkdxgames.plexonranks.model;

import java.util.List;
import java.util.Locale;

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
        tier = tier == null || tier.isBlank() || "Progression".equalsIgnoreCase(tier)
                ? inferredTier(id) : tier.trim();
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
        this(id, order, "", "", enabled, visible, defaultRank, bypassPermission,
                display, requirements, rewards, announce, menu);
    }

    private static String inferredTier(String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("unranked")) return "Starter";
        String base = id;
        int dash = id.indexOf('-');
        if (dash > 0) base = id.substring(0, dash);
        String normalized = base.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "Progression";
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
