package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankRegistry;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.model.RewardType;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ConfigurationValidator {
    List<ValidationIssue> validate(YamlConfiguration config, YamlConfiguration ranksYaml, YamlConfiguration menus,
                                   YamlConfiguration messages, List<Rank> ranks, TextFormatter formatter) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateSchema("config.yml", config, issues);
        validateSchema("ranks.yml", ranksYaml, issues);
        validateSchema("menus.yml", menus, issues);
        validateSchema("messages.yml", messages, issues);
        if (!"SQLITE".equalsIgnoreCase(config.getString("storage.type", "SQLITE"))) {
            issues.add(error("config.yml:storage.type", "PlexonRanks currently supports SQLITE storage."));
        }
        if (!config.getBoolean("formatting.minimessage", true)
                && !config.getBoolean("formatting.legacy-ampersand-support", true)) {
            issues.add(warning("config.yml:formatting", "Both formatting engines are disabled; all tags and color codes will be shown as plain text."));
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
        Set<Integer> navigationSlots = new HashSet<>();
        for (String key : List.of("previous", "info", "close", "next")) {
            int slot = menus.getInt("rank-list.navigation." + key + ".slot", -1);
            if (slot < 0 || slot >= size) {
                issues.add(error("menus.yml:rank-list.navigation." + key + ".slot", "Navigation slot is outside the inventory."));
            } else if (!navigationSlots.add(slot)) {
                issues.add(error("menus.yml:rank-list.navigation." + key + ".slot", "Navigation items cannot share slot " + slot + "."));
            } else if (slots.contains(slot)) {
                issues.add(error("menus.yml:rank-list.navigation." + key + ".slot", "Navigation slot " + slot + " overlaps a rank slot."));
            }
        }
        validateMaterials(menus, issues);
        validateFormatting(messages, "messages.yml", formatter, issues);
        validateFormatting(menus, "menus.yml", formatter, issues);
        for (Rank rank : ranks) {
            if (rank.defaultRank() && !rank.enabled()) {
                issues.add(error("ranks.yml:ranks." + rank.id(), "The default rank must be enabled."));
            }
            if (rank.tier().isBlank()) {
                issues.add(error("ranks.yml:ranks." + rank.id() + ".tier", "Tier/category cannot be blank."));
            }
            if (!rank.menu().material().isBlank() && Material.matchMaterial(rank.menu().material()) == null) {
                issues.add(error("ranks.yml:ranks." + rank.id() + ".menu.material", "Unknown material " + rank.menu().material() + "."));
            }
            if (!formatter.valid(rank.display().name()) || !formatter.valid(rank.display().tag())
                    || !formatter.valid(rank.menu().name())) {
                issues.add(error("ranks.yml:ranks." + rank.id() + ".display", "Malformed formatting in rank name or tag."));
            }
            for (String line : rank.display().description()) {
                if (!formatter.valid(line)) {
                    issues.add(error("ranks.yml:ranks." + rank.id() + ".display.description", "Malformed formatting: " + line));
                }
            }
            for (String line : rank.menu().lore()) {
                if (!formatter.valid(line)) {
                    issues.add(error("ranks.yml:ranks." + rank.id() + ".menu.lore", "Malformed formatting: " + line));
                }
            }
            rank.requirements().stream().filter(requirement -> requirement.type() == RequirementType.ITEM).forEach(requirement -> {
                String material = requirement.string("material", "");
                Material matched = Material.matchMaterial(material);
                if (matched == null || matched.isAir()) {
                    issues.add(error("ranks.yml:ranks." + rank.id() + ".requirements", "Unknown item material " + material + "."));
                }
            });
            rank.rewards().forEach(reward -> {
                if (reward.type() == RewardType.ITEM) {
                    String material = reward.string("material", "");
                    Material matched = Material.matchMaterial(material);
                    if (matched == null || matched.isAir()) {
                        issues.add(error("ranks.yml:ranks." + rank.id() + ".rewards", "Unknown item reward material " + material + "."));
                    }
                }
                if (reward.type() == RewardType.LUCKPERMS_GROUP) {
                    String mode = reward.string("mode", "ADD").toUpperCase(java.util.Locale.ROOT);
                    if (!List.of("ADD", "SET_PRIMARY").contains(mode)) {
                        issues.add(error("ranks.yml:ranks." + rank.id() + ".rewards", "LuckPerms group mode must be ADD or SET_PRIMARY."));
                    }
                }
                for (String line : reward.display()) {
                    if (!formatter.valid(line)) {
                        issues.add(error("ranks.yml:ranks." + rank.id() + ".rewards.display", "Malformed formatting: " + line));
                    }
                }
            });
        }
        if (issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR)) {
            try {
                new RankRegistry(ranks);
            } catch (IllegalArgumentException exception) {
                issues.add(error("ranks.yml:progression", exception.getMessage()));
            }
        }
        return issues;
    }

    private void validateSchema(String source, YamlConfiguration yaml, List<ValidationIssue> issues) {
        int version = yaml.getInt("schema-version", 1);
        if (version > ConfigUpgradeService.CURRENT_SCHEMA) {
            issues.add(error(source + ":schema-version", "Schema " + version + " is newer than this plugin supports."));
        } else if (version < ConfigUpgradeService.CURRENT_SCHEMA) {
            issues.add(warning(source + ":schema-version", "Schema " + version + " is older than schema "
                    + ConfigUpgradeService.CURRENT_SCHEMA + ". Restart to run the safe upgrader."));
        }
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
        return new ValidationIssue(ValidationIssue.Severity.ERROR, source, message == null ? "Unknown validation error" : message);
    }

    private static ValidationIssue warning(String source, String message) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, source, message);
    }
}
