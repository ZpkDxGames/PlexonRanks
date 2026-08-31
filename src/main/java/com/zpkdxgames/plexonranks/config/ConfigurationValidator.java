package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ConfigurationValidator {
    List<ValidationIssue> validate(YamlConfiguration config, YamlConfiguration menus, YamlConfiguration messages,
                                   List<Rank> ranks, TextFormatter formatter) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (!"SQLITE".equalsIgnoreCase(config.getString("storage.type", "SQLITE"))) {
            issues.add(error("config.yml:storage.type", "PlexonRanks 1.0 supports SQLITE storage."));
        }
        int size = menus.getInt("rank-list.size", 54);
        if (size < 9 || size > 54 || size % 9 != 0) {
            issues.add(error("menus.yml:rank-list.size", "Inventory size must be a multiple of 9 between 9 and 54."));
        }
        Set<Integer> slots = new HashSet<>();
        for (int slot : menus.getIntegerList("rank-list.rank-slots")) {
            if (slot < 0 || slot >= size) {
                issues.add(error("menus.yml:rank-list.rank-slots", "Rank slot " + slot + " is outside the inventory."));
            }
            if (!slots.add(slot)) {
                issues.add(error("menus.yml:rank-list.rank-slots", "Duplicate rank slot " + slot + "."));
            }
        }
        if (slots.isEmpty()) {
            issues.add(error("menus.yml:rank-list.rank-slots", "At least one rank slot is required."));
        }
        validateMaterials(menus, issues);
        validateFormatting(messages, "messages.yml", formatter, issues);
        validateFormatting(menus, "menus.yml", formatter, issues);
        for (Rank rank : ranks) {
            if (!rank.menu().material().isBlank() && Material.matchMaterial(rank.menu().material()) == null) {
                issues.add(error("ranks.yml:ranks." + rank.id() + ".menu.material", "Unknown material " + rank.menu().material() + "."));
            }
            if (!formatter.valid(rank.display().name()) || !formatter.valid(rank.display().tag())) {
                issues.add(error("ranks.yml:ranks." + rank.id() + ".display", "Malformed formatting in rank name or tag."));
            }
            for (String line : rank.menu().lore()) {
                if (!formatter.valid(line)) {
                    issues.add(error("ranks.yml:ranks." + rank.id() + ".menu.lore", "Malformed formatting: " + line));
                }
            }
        }
        return issues;
    }

    private void validateMaterials(YamlConfiguration menus, List<ValidationIssue> issues) {
        List<String> paths = List.of(
                "rank-list.rank-template.material", "rank-list.filler.material",
                "rank-list.states.completed.material", "rank-list.states.current.material",
                "rank-list.states.next.material", "rank-list.states.locked.material", "rank-list.states.max.material",
                "rank-list.navigation.previous.material", "rank-list.navigation.info.material",
                "rank-list.navigation.close.material", "rank-list.navigation.next.material"
        );
        for (String path : paths) {
            String value = menus.getString(path, "");
            if (!value.isBlank() && Material.matchMaterial(value) == null) {
                issues.add(error("menus.yml:" + path, "Unknown material " + value + "."));
            }
        }
    }

    private void validateFormatting(ConfigurationSection section, String source, TextFormatter formatter,
                                    List<ValidationIssue> issues) {
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof String string && !formatter.valid(string)) {
                issues.add(error(source + ":" + key, "Malformed MiniMessage/legacy formatting."));
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String string && !formatter.valid(string)) {
                        issues.add(error(source + ":" + key, "Malformed formatting in list entry."));
                    }
                }
            }
        }
    }

    private static ValidationIssue error(String source, String message) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, source, message);
    }
}

