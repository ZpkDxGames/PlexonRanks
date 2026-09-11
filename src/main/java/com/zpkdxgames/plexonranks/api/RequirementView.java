package com.zpkdxgames.plexonranks.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RequirementView(
        String type,
        double current,
        double required,
        double progress,
        boolean complete,
        Map<String, String> values
) {
    public RequirementView {
        progress = Math.max(0.0, Math.min(1.0, progress));
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
