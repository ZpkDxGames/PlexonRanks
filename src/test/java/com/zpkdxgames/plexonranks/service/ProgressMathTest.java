package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressMathTest {
    @Test
    void averagesNormalizedCompletionInsteadOfUnlikeRawUnits() {
        RequirementDefinition money = new RequirementDefinition(RequirementType.MONEY, 100_000, true, Map.of());
        RequirementDefinition xp = new RequirementDefinition(RequirementType.XP_LEVELS, 10, true, Map.of());
        RequirementProgress moneyProgress = new RequirementProgress(money, 50_000, 100_000, false, .5, Map.of());
        RequirementProgress xpProgress = new RequirementProgress(xp, 10, 10, true, 1, Map.of());

        assertEquals(.75, RequirementEngine.overallProgress(List.of(moneyProgress, xpProgress)), 0.00001);
        assertEquals(1.0, RequirementEngine.overallProgress(List.of()), 0.00001);
    }
}

