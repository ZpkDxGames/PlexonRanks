package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankRegistry;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.model.RewardDefinition;
import com.zpkdxgames.plexonranks.model.RewardType;
import com.zpkdxgames.plexonranks.util.NumberFormats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Phase 3 presentation mapping. It never evaluates, consumes, grants, or persists progression state. */
public final class RankPresentation {
    private RankPresentation() {
    }

    public record RequirementCard(
            RequirementType type,
            String title,
            String current,
            String required,
            String missing,
            boolean complete,
            double normalized,
            boolean percentageBased,
            String nextStep
    ) {
        public RequirementCard {
            normalized = Math.max(0.0, Math.min(1.0, normalized));
        }
    }

    public static RequirementCard requirement(RequirementProgress progress) {
        RequirementType type = progress.definition().type();
        String current = progress.placeholders().getOrDefault("current", NumberFormats.number(progress.current()));
        String required = progress.placeholders().getOrDefault("required", NumberFormats.number(progress.required()));
        String missing = progress.placeholders().getOrDefault("missing", NumberFormats.number(progress.missing()));
        String title;
        boolean percentageBased = true;
        String nextStep;

        switch (type) {
            case MONEY -> {
                title = "Money";
                current = "$" + current;
                required = "$" + required;
                missing = "$" + missing;
                nextStep = progress.complete() ? "Complete" : "Earn $" + NumberFormats.number(progress.missing()) + " more";
            }
            case XP_LEVELS -> {
                title = "XP Levels";
                nextStep = progress.complete() ? "Complete" : "Earn " + NumberFormats.number(progress.missing()) + " more XP levels";
            }
            case PLAYTIME -> {
                title = "Playtime";
                current = progress.placeholders().getOrDefault("current_formatted", NumberFormats.durationMinutes(progress.current()));
                required = progress.placeholders().getOrDefault("required_formatted", NumberFormats.durationMinutes(progress.required()));
                missing = NumberFormats.durationMinutes(progress.missing());
                nextStep = progress.complete() ? "Complete" : "Play " + missing + " more";
            }
            case PERMISSION -> {
                title = "Required Access";
                current = progress.complete() ? "Unlocked" : "Not unlocked";
                required = "Required";
                missing = progress.complete() ? "None" : "Required access";
                percentageBased = false;
                nextStep = progress.complete() ? "Complete" : "Unlock the required access";
            }
            case PLACEHOLDER -> {
                title = firstNonBlank(
                        progress.definition().string("label", ""),
                        progress.definition().string("name", ""),
                        "Server Objective");
                percentageBased = false;
                nextStep = progress.complete() ? "Complete" : "Complete the required server objective";
            }
            case ITEM -> {
                String material = progress.definition().string("material", "Item");
                title = pretty(material);
                nextStep = progress.complete() ? "Complete" : "Collect " + NumberFormats.number(progress.missing()) + " more " + pretty(material);
            }
            default -> throw new IllegalStateException("Unsupported requirement presentation: " + type);
        }
        return new RequirementCard(type, title, current, required, missing, progress.complete(), progress.normalized(), percentageBased, nextStep);
    }

    public static String blocker(List<RequirementProgress> progress) {
        return progress.stream()
                .filter(value -> !value.complete())
                .findFirst()
                .map(RankPresentation::requirement)
                .map(RequirementCard::nextStep)
                .orElse("All requirements complete");
    }

    public static String stateLabel(RankState state, boolean ready) {
        return switch (state) {
            case COMPLETED -> "COMPLETED";
            case CURRENT -> "CURRENT RANK";
            case NEXT -> ready ? "READY TO RANK UP" : "NEXT RANK";
            case LOCKED -> "LOCKED";
            case MAX -> "MAXIMUM RANK • MASTERED";
        };
    }

    public static List<String> rewards(Rank rank) {
        List<String> lines = new ArrayList<>();
        for (RewardDefinition reward : rank.rewards()) {
            if (!reward.display().isEmpty()) {
                lines.addAll(reward.display());
                continue;
            }
            lines.add(fallbackReward(reward));
        }
        return lines.isEmpty() ? List.of("<!italic><gray>No configured rewards.</gray>") : List.copyOf(lines);
    }

    public static String historyCause(String cause) {
        String value = cause == null ? "" : cause.toUpperCase(Locale.ROOT);
        if (value.contains("RANKUP")) return "Ranked up";
        if (value.contains("ADMIN")) return "Admin adjustment";
        if (value.contains("MIGRAT") || value.contains("IMPORT")) return "Migration/import";
        if (value.contains("REPAIR") || value.contains("RECOVER") || value.contains("ROLLBACK")) return "Recovered change";
        return "Rank updated";
    }

    public static String historyStatus(String status) {
        String value = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (value.equals("COMPLETED")) return "Completed";
        if (value.startsWith("ROLLED_BACK")) return "Recovered safely";
        if (value.equals("RANK_SAVED")) return "Recorded";
        if (value.equals("INTERRUPTED")) return "Interrupted safely";
        if (value.equals("EXTERNAL_REWARD_FAILED")) return "Completed with a reward issue";
        return "Recorded";
    }

    public static String rankName(RankRegistry registry, String id, String fallback) {
        if (id == null || id.isBlank()) return fallback;
        return registry.byId(id).map(rank -> rank.display().name()).orElse(fallback);
    }

    private static String fallbackReward(RewardDefinition reward) {
        return switch (reward.type()) {
            case MONEY -> "<!italic><gold>$" + NumberFormats.number(reward.number("amount", 0)) + "</gold> <gray>currency</gray>";
            case XP_LEVELS -> "<!italic><yellow>" + NumberFormats.number(reward.number("amount", 0)) + " XP levels</yellow>";
            case ITEM -> "<!italic><white>" + Math.max(1, reward.integer("amount", 1)) + "× "
                    + pretty(reward.string("material", "Item")) + "</white>";
            case PERMISSION -> "<!italic><aqua>New rank permission benefit</aqua>";
            case LUCKPERMS_GROUP -> "<!italic><aqua>Rank access update</aqua>";
            case COMMAND -> "<!italic><light_purple>Special rank reward</light_purple>";
        };
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    static String pretty(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        if (lower.isEmpty()) return "Item";
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
