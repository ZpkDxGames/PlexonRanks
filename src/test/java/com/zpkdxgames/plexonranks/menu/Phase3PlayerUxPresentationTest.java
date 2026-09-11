package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankDisplay;
import com.zpkdxgames.plexonranks.model.RankMenu;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.model.RewardDefinition;
import com.zpkdxgames.plexonranks.model.RewardType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase3PlayerUxPresentationTest {
    @Test void dashboardPrioritizesRequirementsWhenIncomplete() {
        RankDashboardViewModel model = model(progress(RequirementType.MONEY, 500, 1000, false, Map.of()), Optional.of(rank("next", 1)));
        assertEquals(RankDashboardViewModel.PrimaryAction.VIEW_REQUIREMENTS, model.primaryAction()); assertEquals(1, model.requirementsRemaining());
    }
    @Test void dashboardPrioritizesRankupWhenReady() {
        RankDashboardViewModel model = model(progress(RequirementType.XP_LEVELS, 20, 20, true, Map.of()), Optional.of(rank("next", 1)));
        assertEquals(RankDashboardViewModel.PrimaryAction.RANK_UP, model.primaryAction()); assertTrue(model.readyToRankUp());
    }
    @Test void dashboardUsesMasteryActionAtMaximumRank() {
        RankDashboardViewModel model = RankDashboardViewModel.create(rank("master", 9), Optional.empty(), List.of(), List.of());
        assertEquals(RankDashboardViewModel.PrimaryAction.VIEW_PROGRESSION, model.primaryAction()); assertTrue(model.maximum());
    }
    @Test void moneyRequirementIsHumanReadable() {
        var card = RankPresentation.requirement(progress(RequirementType.MONEY, 500, 1000, false, Map.of()));
        assertEquals("Money", card.title()); assertEquals("$500", card.current()); assertEquals("$1,000", card.required()); assertEquals("Earn $500 more", card.nextStep());
    }
    @Test void xpRequirementIsHumanReadable() {
        var card = RankPresentation.requirement(progress(RequirementType.XP_LEVELS, 12, 20, false, Map.of())); assertEquals("XP Levels", card.title()); assertTrue(card.nextStep().contains("8 more XP levels"));
    }
    @Test void playtimeRequirementUsesFormattedValues() {
        var card = RankPresentation.requirement(progress(RequirementType.PLAYTIME, 18, 60, false, Map.of("current_formatted", "18m", "required_formatted", "1h 0m")));
        assertEquals("18m", card.current()); assertEquals("1h 0m", card.required()); assertEquals("Play 42m more", card.nextStep());
    }
    @Test void permissionRequirementDoesNotExposePermissionNode() {
        RequirementDefinition d = new RequirementDefinition(RequirementType.PERMISSION, 1, false, Map.of("permission", "plexonranks.secret.internal"));
        var card = RankPresentation.requirement(new RequirementProgress(d, 0, 1, false, 0, Map.of()));
        assertEquals("Required Access", card.title()); assertFalse(card.nextStep().contains("plexonranks")); assertFalse(card.percentageBased());
    }
    @Test void placeholderRequirementUsesConfiguredPlayerLabel() {
        RequirementDefinition d = new RequirementDefinition(RequirementType.PLACEHOLDER, 40, false, Map.of("placeholder", "%internal_stat%", "label", "Blocks Broken"));
        var card = RankPresentation.requirement(new RequirementProgress(d, 24, 40, false, .6, Map.of("current", "24", "required", "40")));
        assertEquals("Blocks Broken", card.title()); assertFalse(card.title().contains("%")); assertFalse(card.percentageBased());
    }
    @Test void placeholderRequirementFallsBackWithoutLeakingRawKey() {
        RequirementDefinition d = new RequirementDefinition(RequirementType.PLACEHOLDER, 40, false, Map.of("placeholder", "%server_internal_counter%"));
        var card = RankPresentation.requirement(new RequirementProgress(d, 24, 40, false, .6, Map.of("current", "24", "required", "40")));
        assertEquals("Server Objective", card.title()); assertFalse(card.title().contains("server_internal_counter"));
    }
    @Test void itemRequirementDescribesRemainingAmount() {
        RequirementDefinition d = new RequirementDefinition(RequirementType.ITEM, 16, true, Map.of("material", "DIAMOND"));
        var card = RankPresentation.requirement(new RequirementProgress(d, 6, 16, false, .375, Map.of("current", "6", "required", "16", "missing", "10")));
        assertEquals("Diamond", card.title()); assertEquals("Collect 10 more Diamond", card.nextStep());
    }
    @Test void progressIsClampedForPresentation() {
        var card = new RankPresentation.RequirementCard(RequirementType.XP_LEVELS, "XP Levels", "20", "10", "0", true, 1.8, true, "Complete"); assertEquals(1.0, card.normalized());
    }
    @Test void blockerUsesFirstAuthoritativeIncompleteRequirement() {
        List<RequirementProgress> p = List.of(progress(RequirementType.XP_LEVELS, 10, 10, true, Map.of()), progress(RequirementType.MONEY, 2500, 5000, false, Map.of()), progress(RequirementType.PLAYTIME, 10, 60, false, Map.of()));
        assertEquals("Earn $2,500 more", RankPresentation.blocker(p));
    }
    @Test void nextStateBecomesReadyToRankupOnlyWhenReady() {
        assertEquals("NEXT RANK", RankPresentation.stateLabel(RankState.NEXT, false)); assertEquals("READY TO RANK UP", RankPresentation.stateLabel(RankState.NEXT, true)); assertEquals("MAXIMUM RANK • MASTERED", RankPresentation.stateLabel(RankState.MAX, true));
    }
    @Test void commandRewardNeverUsesExecutableCommandAsFallback() {
        RewardDefinition command = new RewardDefinition(RewardType.COMMAND, true, false, List.of("give %player% diamond 64"), List.of(), List.of(), Map.of());
        String rendered = String.join(" ", RankPresentation.rewards(rank("target", 1, List.of(command))));
        assertTrue(rendered.contains("Special rank reward")); assertFalse(rendered.contains("give %player%")); assertFalse(rendered.contains("diamond 64"));
    }
    @Test void moneyAndItemRewardsHaveHumanFallbacks() {
        RewardDefinition money = new RewardDefinition(RewardType.MONEY, true, false, List.of(), List.of(), List.of(), Map.of("amount", 12500));
        RewardDefinition item = new RewardDefinition(RewardType.ITEM, true, false, List.of(), List.of(), List.of(), Map.of("amount", 3, "material", "DIAMOND"));
        String rendered = String.join(" ", RankPresentation.rewards(rank("target", 1, List.of(money, item)))); assertTrue(rendered.contains("$12,500")); assertTrue(rendered.contains("3× Diamond"));
    }
    @Test void historyCauseAndStatusAreTranslated() {
        assertEquals("Ranked up", RankPresentation.historyCause("RANKUP")); assertEquals("Admin adjustment", RankPresentation.historyCause("ADMIN_SET")); assertEquals("Migration/import", RankPresentation.historyCause("MIGRATION"));
        assertEquals("Recovered safely", RankPresentation.historyStatus("ROLLED_BACK_REWARD_FAILURE")); assertEquals("Recorded", RankPresentation.historyStatus("SOME_NEW_INTERNAL_STATUS"));
    }

    private static RankDashboardViewModel model(RequirementProgress progress, Optional<Rank> next) { return RankDashboardViewModel.create(rank("current", 0), next, List.of(progress), List.of("Reward")); }
    private static RequirementProgress progress(RequirementType type, double current, double required, boolean complete, Map<String,String> extra) {
        return new RequirementProgress(new RequirementDefinition(type, required, false, Map.of()), current, required, complete, required <= 0 ? 1 : Math.min(1, current / required), extra);
    }
    private static Rank rank(String id, int order) { return rank(id, order, List.of()); }
    private static Rank rank(String id, int order, List<RewardDefinition> rewards) {
        return new Rank(id, order, true, true, order == 0, "", new RankDisplay("<white>" + id + "</white>", id, id, List.of()), List.of(), rewards, false, new RankMenu(true, "PAPER", 1, 0, false, "", List.of()));
    }
}
