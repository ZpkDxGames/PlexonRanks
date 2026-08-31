package com.zpkdxgames.plexonranks.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record RewardDefinition(
        RewardType type,
        boolean oneTime,
        boolean persistent,
        List<String> commands,
        List<String> permissions,
        List<String> display,
        Map<String, Object> options
) {
    public RewardDefinition {
        commands = List.copyOf(commands);
        permissions = List.copyOf(permissions);
        display = List.copyOf(display);
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public String string(String key, String fallback) {
        Object value = options.get(key.toLowerCase(Locale.ROOT));
        return value == null ? fallback : String.valueOf(value);
    }

    public double number(String key, double fallback) {
        Object value = options.get(key.toLowerCase(Locale.ROOT));
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public int integer(String key, int fallback) {
        return (int) Math.round(number(key, fallback));
    }
}

