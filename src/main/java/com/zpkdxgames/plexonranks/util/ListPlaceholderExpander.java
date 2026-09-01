package com.zpkdxgames.plexonranks.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ListPlaceholderExpander {
    private ListPlaceholderExpander() {
    }

    public static List<String> expand(List<String> template, Map<String, List<String>> listPlaceholders) {
        List<String> result = new ArrayList<>();
        for (String line : template) {
            List<String> replacement = listPlaceholders.get(line);
            if (replacement != null) {
                result.addAll(replacement);
            } else {
                // Scalar placeholders deliberately remain unresolved here. TextFormatter replaces
                // them as Components later, allowing MiniMessage templates and legacy values to mix.
                result.add(line);
            }
        }
        return List.copyOf(result);
    }
}
