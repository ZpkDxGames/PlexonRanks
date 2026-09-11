package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;

import java.util.List;
import java.util.Optional;

/** Immutable projection of authoritative rank snapshots for player-facing menus. */
public record RankDashboardViewModel(
        Rank current,
        Optional<Rank> next,
        List<RequirementProgress> requirements,
        List<RankPresentation.RequirementCard> requirementCards,
        List<String> rewards,
        long requirementsComplete,
        double readiness,
        String blocker,
        PrimaryAction primaryAction
) {
    public enum PrimaryAction {
        VIEW_PROGRESSION,
        VIEW_REQUIREMENTS,
        RANK_UP
    }

    public RankDashboardViewModel {
        next = next == null ? Optional.empty() : next;
        requirements = List.copyOf(requirements == null ? List.of() : requirements);
        requirementCards = List.copyOf(requirementCards == null ? List.of() : requirementCards);
        rewards = List.copyOf(rewards == null ? List.of() : rewards);
        readiness = Math.max(0.0, Math.min(1.0, readiness));
        blocker = blocker == null ? "" : blocker;
    }

    public static RankDashboardViewModel create(Rank current, Optional<Rank> next,
                                                List<RequirementProgress> requirements,
                                                List<String> rewards) {
        Optional<Rank> safeNext = next == null ? Optional.empty() : next;
        List<RequirementProgress> safeProgress = List.copyOf(requirements == null ? List.of() : requirements);
        long completed = safeProgress.stream().filter(RequirementProgress::complete).count();
        double readiness = safeNext.isEmpty() ? 1.0 : RequirementEngine.overallProgress(safeProgress);
        boolean ready = safeNext.isPresent() && safeProgress.stream().allMatch(RequirementProgress::complete);
        PrimaryAction action = safeNext.isEmpty() ? PrimaryAction.VIEW_PROGRESSION
                : ready ? PrimaryAction.RANK_UP : PrimaryAction.VIEW_REQUIREMENTS;
        return new RankDashboardViewModel(
                current,
                safeNext,
                safeProgress,
                safeProgress.stream().map(RankPresentation::requirement).toList(),
                rewards,
                completed,
                readiness,
                safeNext.isEmpty() ? "Progression mastered" : RankPresentation.blocker(safeProgress),
                action
        );
    }

    public boolean maximum() {
        return next.isEmpty();
    }

    public boolean readyToRankUp() {
        return primaryAction == PrimaryAction.RANK_UP;
    }

    public int requirementsRemaining() {
        return Math.max(0, requirements.size() - (int) requirementsComplete);
    }
}
