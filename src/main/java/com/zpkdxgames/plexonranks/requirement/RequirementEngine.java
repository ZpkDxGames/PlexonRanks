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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RequirementEngine {
    private final Map<RequirementType, RequirementHandler> handlers = new EnumMap<>(RequirementType.class);
    private final MoneyHandler moneyHandler;
    private final PlaytimeHandler playtimeHandler;
    private final PermissionHandler permissionHandler;
    private final ItemHandler itemHandler;

    public RequirementEngine(VaultHook vault, PlaceholderHook placeholders) {
        this.moneyHandler = new MoneyHandler(vault);
        this.playtimeHandler = new PlaytimeHandler();
        this.permissionHandler = new PermissionHandler();
        this.itemHandler = new ItemHandler();
        register(moneyHandler);
        register(new XpLevelsHandler());
        register(playtimeHandler);
        register(permissionHandler);
        register(new PlaceholderHandler(placeholders));
        register(itemHandler);
    }

    public void register(RequirementHandler handler) {
        handlers.put(handler.type(), handler);
    }

    public List<RequirementProgress> evaluate(Player player, List<RequirementDefinition> definitions) {
        if (definitions.isEmpty()) {
            return List.of();
        }

        boolean hasMoney = definitions.stream().anyMatch(definition -> definition.type() == RequirementType.MONEY);
        boolean hasPlaytime = definitions.stream().anyMatch(definition -> definition.type() == RequirementType.PLAYTIME);
        boolean hasItems = definitions.stream().anyMatch(definition -> definition.type() == RequirementType.ITEM);

        double moneyBalance = hasMoney ? moneyHandler.balance(player) : 0.0;
        int playtimeTicks = hasPlaytime ? player.getStatistic(Statistic.PLAY_ONE_MINUTE) : 0;
        Map<String, Boolean> permissionResults = new HashMap<>();
        Map<Material, Integer> itemCounts = hasItems ? itemHandler.countRequiredMaterials(player, definitions) : Map.of();

        List<RequirementProgress> result = new ArrayList<>(definitions.size());
        for (RequirementDefinition definition : definitions) {
            RequirementProgress progress = switch (definition.type()) {
                case MONEY -> moneyHandler.evaluate(definition, moneyBalance);
                case PLAYTIME -> playtimeHandler.evaluate(definition, playtimeTicks);
                case PERMISSION -> permissionHandler.evaluate(player, definition, permissionResults);
                case ITEM -> itemHandler.evaluate(definition, itemCounts);
                default -> handler(definition.type()).evaluate(player, definition);
            };
            result.add(progress);
        }
        return List.copyOf(result);
    }

    public RequirementPlan plan(Player player, List<RequirementDefinition> definitions) {
        return new RequirementPlan(definitions, evaluate(player, definitions));
    }

    public List<Consumption> consume(Player player, List<RequirementDefinition> definitions) {
        return consume(player, plan(player, definitions));
    }

    public List<Consumption> consume(Player player, RequirementPlan plan) {
        if (!plan.complete()) {
            throw new IllegalStateException("Requirement plan is incomplete");
        }

        List<Consumption> consumptions = new ArrayList<>();
        boolean itemBatchConsumed = false;
        try {
            for (RequirementDefinition definition : plan.definitions()) {
                if (!definition.consume()) {
                    continue;
                }
                if (definition.type() == RequirementType.ITEM) {
                    if (!itemBatchConsumed) {
                        consumptions.add(itemHandler.consumeBatch(player, plan.definitions()));
                        itemBatchConsumed = true;
                    }
                    continue;
                }
                consumptions.add(handler(definition.type()).consume(player, definition));
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
                // Rollbacks remain best effort; callers log the originating critical failure.
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
            return evaluate(definition, balance(player));
        }

        private RequirementProgress evaluate(RequirementDefinition definition, double balance) {
            return numeric(definition, balance, definition.amount(), Map.of());
        }

        private double balance(Player player) {
            return vault.economy().map(economy -> economy.getBalance(player)).orElse(0.0);
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
        private final ConcurrentMap<RequirementDefinition, Double> requiredMinutes = new ConcurrentHashMap<>();

        @Override
        public RequirementType type() {
            return RequirementType.PLAYTIME;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            return evaluate(definition, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        }

        private RequirementProgress evaluate(RequirementDefinition definition, int playtimeTicks) {
            double currentMinutes = playtimeTicks / 20.0 / 60.0;
            double required = requiredMinutes.computeIfAbsent(definition,
                    ignored -> toMinutes(definition.amount(), definition.string("unit", "MINUTES")));
            Map<String, String> extra = Map.of(
                    "current_formatted", NumberFormats.durationMinutes(currentMinutes),
                    "required_formatted", NumberFormats.durationMinutes(required)
            );
            return numeric(definition, currentMinutes, required, extra);
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
        private final ConcurrentMap<RequirementDefinition, String> permissions = new ConcurrentHashMap<>();

        @Override
        public RequirementType type() {
            return RequirementType.PERMISSION;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            return evaluate(player, definition, new HashMap<>());
        }

        private RequirementProgress evaluate(Player player, RequirementDefinition definition, Map<String, Boolean> results) {
            String permission = permissions.computeIfAbsent(definition, ignored -> definition.string("permission", ""));
            boolean complete = results.computeIfAbsent(permission, player::hasPermission);
            return numeric(definition, complete ? 1 : 0, 1, Map.of("permission", permission));
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            return Consumption.none();
        }
    }

    private static final class PlaceholderHandler implements RequirementHandler {
        private final PlaceholderHook placeholders;
        private final ConcurrentMap<RequirementDefinition, PlaceholderSpec> specs = new ConcurrentHashMap<>();

        private PlaceholderHandler(PlaceholderHook placeholders) {
            this.placeholders = placeholders;
        }

        @Override
        public RequirementType type() {
            return RequirementType.PLACEHOLDER;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            PlaceholderSpec spec = specs.computeIfAbsent(definition, PlaceholderHandler::compile);
            String current = placeholders.apply(player, spec.placeholder());
            boolean complete = compare(current, spec.required(), spec.operator());
            double normalized = complete ? 1.0 : numericRatio(current, spec.required());
            Map<String, String> values = new LinkedHashMap<>();
            values.put("placeholder", spec.placeholder());
            values.put("current", current);
            values.put("required", spec.required());
            values.put("missing", "");
            values.put("percent", NumberFormats.number(normalized * 100.0));
            values.put("completed", String.valueOf(complete));
            return new RequirementProgress(definition, parse(current, complete ? 1 : 0), parse(spec.required(), 1), complete, normalized, values);
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            return Consumption.none();
        }

        private static PlaceholderSpec compile(RequirementDefinition definition) {
            return new PlaceholderSpec(
                    definition.string("placeholder", ""),
                    definition.string("value", NumberFormats.number(definition.amount())),
                    definition.string("operator", ">=").toUpperCase(Locale.ROOT)
            );
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

        private record PlaceholderSpec(String placeholder, String required, String operator) {
        }
    }

    private static final class ItemHandler implements RequirementHandler {
        private final ConcurrentMap<RequirementDefinition, ItemSpec> specs = new ConcurrentHashMap<>();

        @Override
        public RequirementType type() {
            return RequirementType.ITEM;
        }

        @Override
        public RequirementProgress evaluate(Player player, RequirementDefinition definition) {
            ItemSpec spec = spec(definition);
            int current = 0;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item != null && item.getType() == spec.material()) {
                    current += item.getAmount();
                }
            }
            return progress(definition, spec, current);
        }

        private RequirementProgress evaluate(RequirementDefinition definition, Map<Material, Integer> counts) {
            ItemSpec spec = spec(definition);
            return progress(definition, spec, counts.getOrDefault(spec.material(), 0));
        }

        private Map<Material, Integer> countRequiredMaterials(Player player, List<RequirementDefinition> definitions) {
            Map<Material, Integer> counts = new HashMap<>();
            for (RequirementDefinition definition : definitions) {
                if (definition.type() == RequirementType.ITEM) {
                    counts.putIfAbsent(spec(definition).material(), 0);
                }
            }
            if (counts.isEmpty()) {
                return Map.of();
            }
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item != null && counts.containsKey(item.getType())) {
                    counts.merge(item.getType(), item.getAmount(), Integer::sum);
                }
            }
            return counts;
        }

        @Override
        public Consumption consume(Player player, RequirementDefinition definition) {
            return consumeBatch(player, List.of(definition));
        }

        private Consumption consumeBatch(Player player, List<RequirementDefinition> definitions) {
            Map<Material, Integer> required = new LinkedHashMap<>();
            for (RequirementDefinition definition : definitions) {
                if (definition.type() != RequirementType.ITEM || !definition.consume()) {
                    continue;
                }
                ItemSpec spec = spec(definition);
                required.merge(spec.material(), spec.required(), Integer::sum);
            }
            if (required.isEmpty()) {
                return Consumption.none();
            }

            Map<Material, Integer> remaining = new LinkedHashMap<>(required);
            ItemStack[] storage = player.getInventory().getStorageContents();
            ItemStack[] updated = new ItemStack[storage.length];
            for (int slot = 0; slot < storage.length; slot++) {
                ItemStack item = storage[slot];
                updated[slot] = item == null ? null : item.clone();
                if (item == null) {
                    continue;
                }
                int needed = remaining.getOrDefault(item.getType(), 0);
                if (needed <= 0) {
                    continue;
                }
                int taken = Math.min(needed, item.getAmount());
                remaining.put(item.getType(), needed - taken);
                if (taken == item.getAmount()) {
                    updated[slot] = null;
                } else {
                    updated[slot].setAmount(item.getAmount() - taken);
                }
            }

            if (remaining.values().stream().anyMatch(value -> value > 0)) {
                throw new IllegalStateException("Required items changed before consumption");
            }

            player.getInventory().setStorageContents(updated);
            Map<Material, Integer> removed = Map.copyOf(required);
            return new Consumption(() -> removed.forEach((material, amount) -> {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(material, amount));
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }));
        }

        private RequirementProgress progress(RequirementDefinition definition, ItemSpec spec, int current) {
            return numeric(definition, current, spec.required(), Map.of("material", spec.prettyMaterial()));
        }

        private ItemSpec spec(RequirementDefinition definition) {
            return specs.computeIfAbsent(definition, ItemHandler::compile);
        }

        private static ItemSpec compile(RequirementDefinition definition) {
            String configured = definition.string("material", "");
            Material material = Material.matchMaterial(configured);
            if (material == null) {
                throw new IllegalStateException("Unknown item material " + configured);
            }
            int required = Math.max(1, definition.integer("amount", (int) Math.ceil(definition.amount())));
            return new ItemSpec(material, required, pretty(material.name()));
        }

        private static String pretty(String value) {
            String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        private record ItemSpec(Material material, int required, String prettyMaterial) {
        }
    }
}
