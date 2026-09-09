package com.zpkdxgames.plexonranks.requirement;

import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementPlanTest {
    @Test
    void completeOnlyWhenEveryCapturedRequirementIsComplete() {
        RequirementDefinition money = definition(RequirementType.MONEY, 100);
        RequirementDefinition xp = definition(RequirementType.XP_LEVELS, 10);

        RequirementPlan complete = new RequirementPlan(
                List.of(money, xp),
                List.of(progress(money, true), progress(xp, true))
        );
        RequirementPlan incomplete = new RequirementPlan(
                List.of(money, xp),
                List.of(progress(money, true), progress(xp, false))
        );

        assertTrue(complete.complete());
        assertFalse(incomplete.complete());
    }

    @Test
    void rejectsMismatchedDefinitionAndProgressSnapshots() {
        RequirementDefinition money = definition(RequirementType.MONEY, 100);

        assertThrows(IllegalArgumentException.class,
                () -> new RequirementPlan(List.of(money), List.of()));
    }

    @Test
    void defensivelyCopiesCapturedLists() {
        RequirementDefinition money = definition(RequirementType.MONEY, 100);
        List<RequirementDefinition> definitions = new java.util.ArrayList<>(List.of(money));
        List<RequirementProgress> progress = new java.util.ArrayList<>(List.of(progress(money, true)));

        RequirementPlan plan = new RequirementPlan(definitions, progress);
        definitions.clear();
        progress.clear();

        assertTrue(plan.complete());
        assertTrue(plan.definitions().size() == 1);
        assertTrue(plan.progress().size() == 1);
    }

    private static RequirementDefinition definition(RequirementType type, double amount) {
        return new RequirementDefinition(type, amount, false, Map.of());
    }

    private static RequirementProgress progress(RequirementDefinition definition, boolean complete) {
        return new RequirementProgress(definition, complete ? definition.amount() : 0,
                definition.amount(), complete, complete ? 1.0 : 0.0, Map.of());
    }
}
