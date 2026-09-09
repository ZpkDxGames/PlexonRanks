package com.zpkdxgames.plexonranks.requirement;

import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementProgress;

import java.util.List;

public record RequirementPlan(
        List<RequirementDefinition> definitions,
        List<RequirementProgress> progress
) {
    public RequirementPlan {
        definitions = List.copyOf(definitions);
        progress = List.copyOf(progress);
        if (definitions.size() != progress.size()) {
            throw new IllegalArgumentException("Requirement plan definitions/progress size mismatch");
        }
    }

    public boolean complete() {
        return progress.stream().allMatch(RequirementProgress::complete);
    }
}
