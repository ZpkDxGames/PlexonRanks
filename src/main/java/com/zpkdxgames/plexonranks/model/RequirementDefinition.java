package com.zpkdxgames.plexonranks.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record RequirementDefinition(
        RequirementType type,
        double amount,
        boolean consume,
        Map<String, Object> options
) {
    public RequirementDefinition {
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public String string(String key, String fallback) {
        Object value = options.get(key.toLowerCase(Locale.ROOT));
        return value == null ? fallback : String.valueOf(value);
    }

    public int integer(String key, int fallback) {
        Object value = options.get(key.toLowerCase(Locale.ROOT));
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

