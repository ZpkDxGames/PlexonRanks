package com.zpkdxgames.plexonranks.requirement;

import com.zpkdxgames.plexonranks.integration.PlaceholderHook;
import com.zpkdxgames.plexonranks.integration.VaultHook;
import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.util.NumberFormats;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RequirementEngine {
    private final Map<RequirementType, RequirementHandler> handlers = new EnumMap<>(RequirementType.class);

    public RequirementEngine(VaultHook vault, PlaceholderHook placeholders) {
        register(new MoneyHandler(vault));
        register(new XpLevelsHandler());
        register(new PlaytimeHandler());
        register(new PermissionHandler());
        register(new PlaceholderHandler(placeholders));
        register(new ItemHandler());
    }

    public void register(RequirementHandler handler) {
        handlers.put(handler.type(), handler);
    }

    public List<RequirementProgress> evaluate(Player player, List<RequirementDefinition> definitions) {
        List<RequirementProgress> result = new ArrayList<>(definitions.size());
        for (RequirementDefinition definition : definitions) {
            RequirementHandler handler = handler(definition.type());
            result.add(handler.evaluate(player, definition));
        }
        return List.copyOf(result);
    }

    public List<Consumption> consume(Player player, List<RequirementDefinition> definitions) {
        List<Consumption> consumptions = new ArrayList<>();
        try {
            for (RequirementDefinition definition : definitions) {
                RequirementHandler handler = handler(definition.type());
                RequirementProgress progress = handler.evaluate(player, definition);
                if (!progress.complete()) {
                    throw new IllegalStateException("Requirement changed before consumption: " + definition.type());
                }
                if (definition.consume()) {
                    consumptions.add(handler.consume(player, definition));
                }
            }
            return List.copyOf(consumptions);
        } catch (RuntimeException exception) {
            rollback(consumptions);
            throw exception;
        }
    }

    public void rollback(List<Consumption> consumptions) {
        List<Consumption> reversed = new ArrayList<>(consumptions);
        Collections.reverse(reversed);
        for (Consumption consumption : reversed) {
            try {
                consumption.rollback().run();
            } catch (RuntimeException ignored) {
                // Rollbacks are best effort; the caller logs the originating critical failure.
            }
        }
    }

    public static double overallProgress(List<RequirementProgress> progress) {
        if (progress.isEmpty()) {
            return 1.0;
        }
        return progress.stream().mapToDouble(RequirementProgress::normalized).average().orElse(0.0);
    }

    private RequirementHandler handler(RequirementType type) {
        RequirementHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("No requirement handler registered for " + type);
        }
        return handler;
    }

    private static RequirementProgress numeric(RequirementDefinition definition, double current, double required,
                                               Map<String, String> extra) {
        boolean complete = current >= required;
        double normalized = required <= 0 ? 1.0 : Math.min(1.0, current / required);
        Map<String, String> placeholders = new LinkedHashMap<>(extra);
        placeholders.put("current", NumberFormats.number(current));
        placeholders.put("required", NumberFormats.number(required));
        placeholders.put("missing", NumberFormats.number(Math.max(0, required - current)));
        placeholders.put("percent", NumberFormats.number(normalized * 100.0));
        placeholders.put("completed", String.valueOf(complete));
        return new RequirementProgress(definition, current, required, complete, normalized, placeholders);
    }

    private static final class MoneyHandler implements RequirementHandler {
        private final VaultHook vault;

        private MoneyHandler(VaultHook vault) {
            this.vault = vault;
        }

        @Override
        public RequirementType type() {
            return RequirementType.MONEY;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            double current = vault.economy().map(economy -> economy.getBalance(player)).orElse(0.0);
            return numeric(definition, current, definition.amount(), Map.of());
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            var economy = vault.economy().orElseThrow(() -> new IllegalStateException("Vault economy is unavailable"));
            EconomyResponse response = economy.withdrawPlayer(player, definition.amount());
            if (!response.transactionSuccess()) {
                throw new IllegalStateException("Vault withdrawal failed: " + response.errorMessage);
            }
            return new Consumption(() -> economy.depositPlayer(player, definition.amount()));
        }
    }

    private static final class XpLevelsHandler implements RequirementHandler {
        @Override
        public RequirementType type() {
            return RequirementType.XP_LEVELS;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            return numeric(definition, player.getLevel(), definition.amount(), Map.of());
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            int oldLevel = player.getLevel();
            int required = (int) Math.ceil(definition.amount());
            if (oldLevel < required) {
                throw new IllegalStateException("Player no longer has enough XP levels");
            }
            player.setLevel(oldLevel - required);
            return new Consumption(() -> player.setLevel(oldLevel));
        }
    }

    private static final class PlaytimeHandler implements RequirementHandler {
        @Override
        public RequirementType type() {
            return RequirementType.PLAYTIME;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            double currentMinutes = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 60.0;
            double requiredMinutes = toMinutes(definition.amount(), definition.string("unit", "MINUTES"));
            Map<String, String> extra = Map.of(
                    "current_formatted", NumberFormats.durationMinutes(currentMinutes),
                    "required_formatted", NumberFormats.durationMinutes(requiredMinutes)
            );
            return numeric(definition, currentMinutes, requiredMinutes, extra);
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            return Consumption.none();
        }

        private static double toMinutes(double amount, String unit) {
            return switch (unit.toUpperCase(Locale.ROOT)) {
                case "SECONDS" -> amount / 60.0;
                case "HOURS" -> amount * 60.0;
                case "DAYS" -> amount * 1440.0;
                default -> amount;
            };
        }
    }

    private static final class PermissionHandler implements RequirementHandler {
        @Override
        public RequirementType type() {
            return RequirementType.PERMISSION;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            boolean complete = player.hasPermission(definition.string("permission", ""));
            return numeric(definition, complete ? 1 : 0, 1, Map.of("permission", definition.string("permission", "")));
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            return Consumption.none();
        }
    }

    private static final class PlaceholderHandler implements RequirementHandler {
        private final PlaceholderHook placeholders;

        private PlaceholderHandler(PlaceholderHook placeholders) {
            this.placeholders = placeholders;
        }

        @Override
        public RequirementType type() {
            return RequirementType.PLACEHOLDER;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            String placeholder = definition.string("placeholder", "");
            String current = placeholders.apply(player, placeholder);
            String required = definition.string("value", NumberFormats.number(definition.amount()));
            String operator = definition.string("operator", ">=").toUpperCase(Locale.ROOT);
            boolean complete = compare(current, required, operator);
            double normalized = complete ? 1.0 : numericRatio(current, required);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("placeholder", placeholder);
            values.put("current", current);
            values.put("required", required);
            values.put("missing", "");
            values.put("percent", NumberFormats.number(normalized * 100.0));
            values.put("completed", String.valueOf(complete));
            return new RequirementProgress(definition, parse(current, complete ? 1 : 0), parse(required, 1), complete, normalized, values);
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            return Consumption.none();
        }

        private static boolean compare(String current, String required, String operator) {
            Double left = parseNullable(current);
            Double right = parseNullable(required);
            if (left != null && right != null) {
                return switch (operator) {
                    case "=" -> Double.compare(left, right) == 0;
                    case "!=" -> Double.compare(left, right) != 0;
                    case ">" -> left > right;
                    case ">=" -> left >= right;
                    case "<" -> left < right;
                    case "<=" -> left <= right;
                    default -> false;
                };
            }
            return switch (operator) {
                case "=" -> current.equalsIgnoreCase(required);
                case "!=" -> !current.equalsIgnoreCase(required);
                case "CONTAINS" -> current.toLowerCase(Locale.ROOT).contains(required.toLowerCase(Locale.ROOT));
                default -> false;
            };
        }

        private static double numericRatio(String current, String required) {
            Double left = parseNullable(current);
            Double right = parseNullable(required);
            return left == null || right == null || right <= 0 ? 0 : Math.max(0, Math.min(1, left / right));
        }

        private static Double parseNullable(String value) {
            try {
                return Double.parseDouble(value.replace(",", "").trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static double parse(String value, double fallback) {
            Double parsed = parseNullable(value);
            return parsed == null ? fallback : parsed;
        }
    }

    private static final class ItemHandler implements RequirementHandler {
        @Override
        public RequirementType type() {
            return RequirementType.ITEM;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            Material material = material(definition);
            int current = 0;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item != null && item.getType() == material) {
                    current += item.getAmount();
                }
            }
            int required = Math.max(1, definition.integer("amount", (int) Math.ceil(definition.amount())));
            return numeric(definition, current, required, Map.of("material", pretty(material.name())));
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            Material material = material(definition);
            int amount = Math.max(1, definition.integer("amount", (int) Math.ceil(definition.amount())));
            Map<Integer, ItemStack> leftovers = player.getInventory().removeItem(new ItemStack(material, amount));
            if (!leftovers.isEmpty()) {
                player.getInventory().addItem(new ItemStack(material, amount - leftovers.values().stream().mapToInt(ItemStack::getAmount).sum()));
                throw new IllegalStateException("Required items changed before consumption");
            }
            return new Consumption(() -> {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(material, amount));
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            });
        }

        private static Material material(RequirementDefinition definition) {
            Material material = Material.matchMaterial(definition.string("material", ""));
            if (material == null) {
                throw new IllegalStateException("Unknown item material " + definition.string("material", ""));
            }
            return material;
        }

        private static String pretty(String value) {
            String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }
}

