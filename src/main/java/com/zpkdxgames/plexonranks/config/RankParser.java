package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankDisplay;
import com.zpkdxgames.plexonranks.model.RankMenu;
import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.model.RewardDefinition;
import com.zpkdxgames.plexonranks.model.RewardType;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class RankParser {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    ParseResult parse(YamlConfiguration yaml) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<Rank> ranks = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("ranks");
        if (root == null || root.getKeys(false).isEmpty()) {
            issues.add(error("ranks.yml", "No ranks are configured."));
            return new ParseResult(ranks, issues);
        }

        Map<Integer, String> orders = new HashMap<>();
        int defaults = 0;
        for (String id : root.getKeys(false)) {
            String path = "ranks." + id;
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                issues.add(error(path, "Rank entry is not a configuration section."));
                continue;
            }
            if (!VALID_ID.matcher(id).matches()) {
                issues.add(error(path, "Rank ID must match " + VALID_ID.pattern() + "."));
            }

            int order = section.getInt("order", Integer.MIN_VALUE);
            if (order == Integer.MIN_VALUE) {
                issues.add(error(path + ".order", "Missing order value."));
                order = ranks.size();
            }
            String previous = orders.put(order, id);
            if (previous != null) {
                issues.add(error(path + ".order", "Order " + order + " is already used by " + previous + "."));
            }

            boolean defaultRank = section.getBoolean("default", false);
            if (defaultRank) {
                defaults++;
            }
            String tier = section.getString("tier", "Progression").trim();
            if (tier.isBlank()) {
                issues.add(error(path + ".tier", "Tier/category cannot be blank."));
                tier = "Progression";
            }
            String next = section.getString("next", "").trim();
            if (!next.isBlank() && !VALID_ID.matcher(next).matches()) {
                issues.add(error(path + ".next", "Next-rank ID must match " + VALID_ID.pattern() + "."));
            }

            ConfigurationSection displaySection = section.getConfigurationSection("display");
            String displayName = displaySection == null ? id : displaySection.getString("name", id);
            String shortName = displaySection == null ? id : displaySection.getString("short-name", id);
            String tag = displaySection == null ? displayName : displaySection.getString("tag", displayName);
            List<String> description = displaySection == null ? List.of() : displaySection.getStringList("description");

            List<RequirementDefinition> requirements = parseRequirements(section, path, issues);
            List<RewardDefinition> rewards = parseRewards(section, path, issues);
            ConfigurationSection menuSection = section.getConfigurationSection("menu");
            RankMenu menu = new RankMenu(
                    menuSection == null || menuSection.getBoolean("use-global-template", true),
                    menuSection == null ? "" : menuSection.getString("material", ""),
                    menuSection == null ? 1 : Math.max(1, Math.min(64, menuSection.getInt("amount", 1))),
                    menuSection == null ? 0 : Math.max(0, menuSection.getInt("custom-model-data", 0)),
                    menuSection != null && menuSection.getBoolean("glow", false),
                    menuSection == null ? "%rank_name%" : menuSection.getString("name", "%rank_name%"),
                    menuSection == null ? List.of() : menuSection.getStringList("lore")
            );

            ranks.add(new Rank(
                    id,
                    order,
                    tier,
                    next,
                    section.getBoolean("enabled", true),
                    section.getBoolean("visible", true),
                    defaultRank,
                    section.getString("bypass-permission", ""),
                    new RankDisplay(displayName, shortName, tag, description),
                    requirements,
                    rewards,
                    section.getBoolean("announce", true),
                    menu
            ));
        }
        if (defaults == 0) {
            issues.add(error("ranks.yml", "Exactly one enabled default/root rank must be marked with default: true."));
        } else if (defaults > 1) {
            issues.add(error("ranks.yml", "Exactly one rank may be marked default."));
        }
        return new ParseResult(ranks, issues);
    }

    private List<RequirementDefinition> parseRequirements(ConfigurationSection rank, String path, List<ValidationIssue> issues) {
        List<RequirementDefinition> result = new ArrayList<>();
        List<Map<?, ?>> entries = rank.getMapList("requirements");
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> map = normalize(entries.get(index));
            String rawType = String.valueOf(map.getOrDefault("type", ""));
            RequirementType type;
            try {
                type = RequirementType.valueOf(rawType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                issues.add(error(path + ".requirements[" + index + "]", "Unknown requirement type: " + rawType));
                continue;
            }
            double amount = number(map.get("amount"), type == RequirementType.PERMISSION || type == RequirementType.PLACEHOLDER ? 1 : 0);
            if (requiresAmount(type) && numberOrNull(map.get("amount")) == null) {
                issues.add(error(path + ".requirements[" + index + "]", "Requirement amount is missing or is not a finite number."));
            } else if (!Double.isFinite(amount) || amount < 0) {
                issues.add(error(path + ".requirements[" + index + "]", "Requirement amount must be a finite, non-negative number."));
            }
            boolean consume = booleanValue(map.get("consume"), false);
            if (List.of(RequirementType.PLAYTIME, RequirementType.PERMISSION, RequirementType.PLACEHOLDER).contains(type) && consume) {
                issues.add(warning(path + ".requirements[" + index + "]", type + " is never consumed; consume was ignored."));
                consume = false;
            }
            if (type == RequirementType.PERMISSION && string(map.get("permission")).isBlank()) {
                issues.add(error(path + ".requirements[" + index + "]", "Permission requirement is missing permission."));
            }
            if (type == RequirementType.ITEM && string(map.get("material")).isBlank()) {
                issues.add(error(path + ".requirements[" + index + "]", "Item requirement is missing material."));
            }
            if (type == RequirementType.PLAYTIME && !List.of("SECONDS", "MINUTES", "HOURS", "DAYS")
                    .contains(string(map.getOrDefault("unit", "MINUTES")).toUpperCase(Locale.ROOT))) {
                issues.add(error(path + ".requirements[" + index + "]", "Playtime unit must be SECONDS, MINUTES, HOURS, or DAYS."));
            }
            if (type == RequirementType.PLACEHOLDER) {
                if (string(map.get("placeholder")).isBlank()) {
                    issues.add(error(path + ".requirements[" + index + "]", "Placeholder requirement is missing placeholder."));
                }
                String operator = string(map.getOrDefault("operator", ">=")).toUpperCase(Locale.ROOT);
                if (!List.of("=", "!=", ">", ">=", "<", "<=", "CONTAINS").contains(operator)) {
                    issues.add(error(path + ".requirements[" + index + "]", "Unsupported placeholder operator: " + operator));
                }
            }
            result.add(new RequirementDefinition(type, amount, consume, map));
        }
        return result;
    }

    private List<RewardDefinition> parseRewards(ConfigurationSection rank, String path, List<ValidationIssue> issues) {
        List<RewardDefinition> result = new ArrayList<>();
        List<Map<?, ?>> entries = rank.getMapList("rewards");
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> map = normalize(entries.get(index));
            String rawType = String.valueOf(map.getOrDefault("type", ""));
            RewardType type;
            try {
                type = RewardType.valueOf(rawType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                issues.add(error(path + ".rewards[" + index + "]", "Unknown reward type: " + rawType));
                continue;
            }
            List<String> commands = stringList(map.get("commands"));
            List<String> permissions = stringList(map.get("permissions"));
            List<String> display = stringList(map.get("display"));
            if (type == RewardType.COMMAND && commands.isEmpty()) {
                issues.add(error(path + ".rewards[" + index + "]", "Command reward has no commands."));
            }
            if (type == RewardType.PERMISSION && permissions.isEmpty()) {
                issues.add(error(path + ".rewards[" + index + "]", "Permission reward has no permissions."));
            }
            if (type == RewardType.LUCKPERMS_GROUP && string(map.get("group")).isBlank()) {
                issues.add(error(path + ".rewards[" + index + "]", "LuckPerms group reward has no group."));
            }
            if (type == RewardType.ITEM && string(map.get("material")).isBlank()) {
                issues.add(error(path + ".rewards[" + index + "]", "Item reward is missing material."));
            }
            if (List.of(RewardType.MONEY, RewardType.XP_LEVELS, RewardType.ITEM).contains(type)) {
                Double amount = numberOrNull(map.get("amount"));
                if (amount == null || amount <= 0) {
                    issues.add(error(path + ".rewards[" + index + "]", "Reward amount must be a finite number greater than zero."));
                }
            }
            if (commands.stream().anyMatch(command -> command.isBlank() || command.contains("\n") || command.contains("\r"))) {
                issues.add(error(path + ".rewards[" + index + "]", "Command rewards cannot contain blank or multiline commands."));
            }
            if (permissions.stream().anyMatch(String::isBlank)) {
                issues.add(error(path + ".rewards[" + index + "]", "Permission rewards cannot contain blank permissions."));
            }
            boolean persistent = booleanValue(map.get("persistent"), type == RewardType.PERMISSION || type == RewardType.LUCKPERMS_GROUP);
            if ((type == RewardType.PERMISSION || type == RewardType.LUCKPERMS_GROUP) && !persistent) {
                issues.add(error(path + ".rewards[" + index + "]",
                        type + " rewards must be persistent in 3.0 so LuckPerms projection can be reconciled safely."));
            }
            result.add(new RewardDefinition(
                    type,
                    booleanValue(map.get("one-time"), type != RewardType.PERMISSION && type != RewardType.LUCKPERMS_GROUP),
                    persistent,
                    commands,
                    permissions,
                    display,
                    map
            ));
        }
        return result;
    }

    private static Map<String, Object> normalize(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key).toLowerCase(Locale.ROOT), value));
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Object value, double fallback) {
        Double parsed = numberOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private static Double numberOrNull(Object value) {
        try {
            double parsed = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    private static boolean requiresAmount(RequirementType type) {
        return type == RequirementType.MONEY || type == RequirementType.XP_LEVELS
                || type == RequirementType.PLAYTIME || type == RequirementType.ITEM;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static ValidationIssue error(String source, String message) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, source, message);
    }

    private static ValidationIssue warning(String source, String message) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, source, message);
    }

    record ParseResult(List<Rank> ranks, List<ValidationIssue> issues) {
    }
}
