package com.zpkdxgames.plexonranks.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RequirementProgress(
        RequirementDefinition definition,
        double current,
        double required,
        boolean complete,
        double normalized,
        Map<String, String> placeholders
) {
    public RequirementProgress {
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
    }

    public double missing() {
        return Math.max(0.0, required - current);
    }
}

