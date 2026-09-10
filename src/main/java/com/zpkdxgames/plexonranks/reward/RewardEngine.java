package com.zpkdxgames.plexonranks.reward;

import com.zpkdxgames.plexonranks.integration.LuckPermsHook;
import com.zpkdxgames.plexonranks.integration.VaultHook;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankRegistry;
import com.zpkdxgames.plexonranks.model.RewardDefinition;
import com.zpkdxgames.plexonranks.model.RewardType;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class RewardEngine {
    private final JavaPlugin plugin;
    private final VaultHook vault;
    private final LuckPermsHook luckPerms;
    private final Supplier<TextFormatter> formatter;
    private final Map<RewardType, RewardHandler> handlers = new EnumMap<>(RewardType.class);
    private volatile RankRegistry persistentPlanRegistry;
    private volatile Map<GrantPlanKey, PersistentGrantPlan> persistentGrantPlans = Map.of();
    private volatile Set<String> managedPermissions = Set.of();
    private volatile Set<String> managedGroups = Set.of();

    public RewardEngine(JavaPlugin plugin, VaultHook vault, LuckPermsHook luckPerms, Supplier<TextFormatter> formatter) {
        this.plugin = plugin;
        this.vault = vault;
        this.luckPerms = luckPerms;
        this.formatter = formatter;
        register(new CommandHandler(plugin));
        register(new PermissionHandler(luckPerms));
        register(new GroupHandler(luckPerms));
        register(new MoneyHandler(vault));
        register(new XpHandler());
        register(new ItemHandler(formatter));
    }

    public void register(RewardHandler handler) {
        handlers.put(handler.type(), handler);
    }

    /** Legacy execution surface retained for internal compatibility; item overflow now fails closed. */
    public CompletableFuture<Void> execute(Player player, Rank rank, Map<String, String> placeholders) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (RewardDefinition reward : rank.rewards()) {
            RewardHandler handler = handlers.get(reward.type());
            if (handler == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("No reward handler registered for " + reward.type()));
            }
            chain = chain.thenCompose(ignored -> onMain(() -> handler.execute(player, rank, reward, placeholders)));
        }
        return chain;
    }

    public synchronized void compilePersistentGrantPlans(RankRegistry registry) {
        Map<GrantPlanKey, PersistentGrantPlan> compiled = new LinkedHashMap<>();
        Set<String> cumulativePermissions = new LinkedHashSet<>();
        List<LuckPermsHook.GroupGrant> cumulativeGroups = new ArrayList<>();
        Set<String> allPermissions = new LinkedHashSet<>();
        Set<String> allGroups = new LinkedHashSet<>();

        for (Rank rank : registry.ordered()) {
            PersistentGrantPlan currentOnly = persistentPlanFor(rank.rewards());
            compiled.put(new GrantPlanKey(rank.id(), false), currentOnly);
            allPermissions.addAll(currentOnly.permissions());
            currentOnly.groups().forEach(group -> allGroups.add(group.group()));

            cumulativePermissions.addAll(currentOnly.permissions());
            cumulativeGroups.addAll(currentOnly.groups());
            compiled.put(new GrantPlanKey(rank.id(), true), new PersistentGrantPlan(
                    Set.copyOf(cumulativePermissions),
                    List.copyOf(cumulativeGroups)
            ));
        }

        persistentGrantPlans = Map.copyOf(compiled);
        managedPermissions = Set.copyOf(allPermissions);
        managedGroups = Set.copyOf(allGroups);
        persistentPlanRegistry = registry;
    }

    public List<String> validateIntegrations(RankRegistry registry) {
        List<String> failures = new ArrayList<>();
        for (Rank rank : registry.ordered()) {
            for (RewardDefinition reward : rank.rewards()) {
                if (reward.type() == RewardType.LUCKPERMS_GROUP) {
                    String group = reward.string("group", "");
                    if (!luckPerms.groupExists(group)) {
                        failures.add("Rank '" + rank.id() + "' references missing LuckPerms group '" + group + "'");
                    }
                }
            }
        }
        return List.copyOf(failures);
    }

    public CompletableFuture<Void> reconcile(UUID uuid, Rank current, RankRegistry registry, boolean cumulative) {
        if (persistentPlanRegistry != registry) {
            compilePersistentGrantPlans(registry);
        }
        PersistentGrantPlan plan = persistentGrantPlans.getOrDefault(
                new GrantPlanKey(current.id(), cumulative), PersistentGrantPlan.EMPTY);
        return luckPerms.reconcileManagedGrants(uuid, plan.permissions(), plan.groups(), managedPermissions, managedGroups);
    }

    /** Validates every known failure that can be determined before custody changes. */
    public void preflight(Player player, Rank rank, Map<String, String> placeholders) {
        requireMainThread("reward preflight");
        ItemStack[] simulated = cloneArray(player.getInventory().getStorageContents());
        for (RewardDefinition reward : rank.rewards()) {
            if (reward.type() == RewardType.COMMAND) {
                for (String configured : reward.commands()) {
                    sanitizeCommand(TextFormatter.replaceRaw(configured, placeholders), rank);
                }
            } else if (reward.type() == RewardType.MONEY && reward.oneTime()) {
                if (vault.economy().isEmpty()) {
                    throw new IllegalStateException("Vault economy is unavailable for MONEY reward");
                }
            } else if (reward.type() == RewardType.LUCKPERMS_GROUP) {
                String group = reward.string("group", "");
                if (!luckPerms.groupExists(group)) {
                    throw new IllegalStateException("LuckPerms group does not exist: " + group);
                }
            } else if (reward.type() == RewardType.ITEM && reward.oneTime()) {
                ItemStack item = buildItem(reward, placeholders);
                if (!mergeInto(simulated, item)) {
                    throw new IllegalStateException("Player inventory cannot safely receive all item rewards for rank " + rank.id());
                }
            }
        }
    }

    /**
     * Executes only one-time rewards that can be compensated locally. Persistent
     * LuckPerms state is reconciled separately, and external commands are deferred.
     */
    public ReversibleRewardBatch executeReversible(Player player, Rank rank, Map<String, String> placeholders) {
        requireMainThread("reversible rewards");
        List<Runnable> rollbacks = new ArrayList<>();
        ItemStack[] inventoryBefore = null;
        try {
            for (RewardDefinition reward : rank.rewards()) {
                if (!reward.oneTime() || reward.type() == RewardType.COMMAND
                        || reward.type() == RewardType.PERMISSION || reward.type() == RewardType.LUCKPERMS_GROUP) {
                    continue;
                }
                switch (reward.type()) {
                    case MONEY -> {
                        var economy = vault.economy().orElseThrow(() -> new IllegalStateException("Vault economy is unavailable"));
                        double amount = reward.number("amount", 0);
                        EconomyResponse response = economy.depositPlayer(player, amount);
                        if (!response.transactionSuccess()) {
                            throw new IllegalStateException("Vault deposit failed: " + response.errorMessage);
                        }
                        rollbacks.add(() -> {
                            EconomyResponse rollback = economy.withdrawPlayer(player, amount);
                            if (!rollback.transactionSuccess()) {
                                throw new IllegalStateException("Vault reward rollback failed: " + rollback.errorMessage);
                            }
                        });
                    }
                    case XP_LEVELS -> {
                        int oldLevel = player.getLevel();
                        float oldExp = player.getExp();
                        int oldTotal = player.getTotalExperience();
                        player.giveExpLevels(Math.max(0, reward.integer("amount", 0)));
                        rollbacks.add(() -> {
                            player.setLevel(oldLevel);
                            player.setExp(oldExp);
                            player.setTotalExperience(oldTotal);
                        });
                    }
                    case ITEM -> {
                        if (inventoryBefore == null) {
                            inventoryBefore = cloneArray(player.getInventory().getStorageContents());
                            ItemStack[] snapshot = cloneArray(inventoryBefore);
                            rollbacks.add(() -> player.getInventory().setStorageContents(cloneArray(snapshot)));
                        }
                        ItemStack item = buildItem(reward, placeholders);
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                        if (!overflow.isEmpty()) {
                            throw new IllegalStateException("Inventory changed after reward preflight; refusing world-drop overflow");
                        }
                    }
                    default -> throw new IllegalStateException("Unsupported reversible reward type " + reward.type());
                }
            }
            return new ReversibleRewardBatch(List.copyOf(rollbacks));
        } catch (RuntimeException exception) {
            rollbackAll(rollbacks, exception);
            throw exception;
        }
    }

    /** Irreversible external command boundary; must be the final reward stage. */
    public void executeCommands(Player player, Rank rank, Map<String, String> placeholders) {
        requireMainThread("command rewards");
        for (RewardDefinition reward : rank.rewards()) {
            if (reward.type() != RewardType.COMMAND || !reward.oneTime()) {
                continue;
            }
            for (String configured : reward.commands()) {
                String command = sanitizeCommand(TextFormatter.replaceRaw(configured, placeholders), rank);
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    throw new IllegalStateException("External reward command was not handled: " + command.split(" ")[0]);
                }
            }
        }
    }

    public static List<String> display(Rank rank) {
        return rank.rewards().stream().flatMap(reward -> reward.display().stream()).toList();
    }

    private PersistentGrantPlan persistentPlanFor(List<RewardDefinition> rewards) {
        Set<String> permissions = new LinkedHashSet<>();
        List<LuckPermsHook.GroupGrant> groups = new ArrayList<>();
        for (RewardDefinition reward : rewards) {
            if (!reward.persistent()) {
                continue;
            }
            if (reward.type() == RewardType.PERMISSION) {
                permissions.addAll(reward.permissions());
            } else if (reward.type() == RewardType.LUCKPERMS_GROUP) {
                groups.add(new LuckPermsHook.GroupGrant(
                        reward.string("group", ""),
                        reward.string("mode", "ADD")
                ));
            }
        }
        return new PersistentGrantPlan(Set.copyOf(permissions), List.copyOf(groups));
    }

    private CompletableFuture<Void> onMain(Supplier<CompletableFuture<Void>> action) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return action.get();
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                action.get().whenComplete((ignored, error) -> {
                    if (error == null) result.complete(null); else result.completeExceptionally(error);
                });
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    private ItemStack buildItem(RewardDefinition reward, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(reward.string("material", "STONE"));
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("Invalid item reward material");
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), reward.integer("amount", 1))));
        ItemMeta meta = item.getItemMeta();
        TextFormatter text = formatter.get();
        String name = reward.string("name", "");
        if (!name.isBlank()) {
            meta.displayName(text.withoutItalics(text.component(name, placeholders)));
        }
        Object loreValue = reward.options().get("lore");
        if (loreValue instanceof List<?> lore) {
            List<Component> components = lore.stream()
                    .map(String::valueOf)
                    .map(line -> text.withoutItalics(text.component(line, placeholders)))
                    .toList();
            meta.lore(components);
        }
        int modelData = reward.integer("custom-model-data", 0);
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }
        Object enchantValue = reward.options().get("enchantments");
        if (enchantValue instanceof Map<?, ?> enchantments) {
            enchantments.forEach((key, value) -> {
                NamespacedKey namespaced = NamespacedKey.fromString(String.valueOf(key).toLowerCase(Locale.ROOT));
                Enchantment enchantment = namespaced == null ? null : Enchantment.getByKey(namespaced);
                if (enchantment != null) {
                    int level = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
                    meta.addEnchant(enchantment, level, true);
                }
            });
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    static boolean mergeInto(ItemStack[] storage, ItemStack source) {
        ItemStack remaining = source.clone();
        for (int slot = 0; slot < storage.length && remaining.getAmount() > 0; slot++) {
            ItemStack existing = storage[slot];
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(remaining)) {
                continue;
            }
            int max = Math.min(existing.getMaxStackSize(), remaining.getMaxStackSize());
            int space = max - existing.getAmount();
            if (space <= 0) continue;
            int moved = Math.min(space, remaining.getAmount());
            ItemStack updated = existing.clone();
            updated.setAmount(existing.getAmount() + moved);
            storage[slot] = updated;
            remaining.setAmount(remaining.getAmount() - moved);
        }
        for (int slot = 0; slot < storage.length && remaining.getAmount() > 0; slot++) {
            ItemStack existing = storage[slot];
            if (existing != null && !existing.getType().isAir()) continue;
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack inserted = remaining.clone();
            inserted.setAmount(moved);
            storage[slot] = inserted;
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining.getAmount() == 0;
    }

    private static String sanitizeCommand(String configured, Rank rank) {
        String command = configured.strip();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.contains("\n") || command.contains("\r") || command.isBlank()) {
            throw new IllegalArgumentException("Unsafe or empty reward command for rank " + rank.id());
        }
        return command;
    }

    private static void rollbackAll(List<Runnable> rollbacks, RuntimeException original) {
        for (int index = rollbacks.size() - 1; index >= 0; index--) {
            try {
                rollbacks.get(index).run();
            } catch (RuntimeException rollbackFailure) {
                original.addSuppressed(rollbackFailure);
            }
        }
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        return Arrays.stream(source).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private static void requireMainThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonRanks " + action + " must run on the primary thread");
        }
    }

    private record GrantPlanKey(String rankId, boolean cumulative) {
    }

    private record PersistentGrantPlan(Set<String> permissions, List<LuckPermsHook.GroupGrant> groups) {
        private static final PersistentGrantPlan EMPTY = new PersistentGrantPlan(Set.of(), List.of());
    }

    public static final class ReversibleRewardBatch {
        private final List<Runnable> rollbacks;
        private boolean rolledBack;

        private ReversibleRewardBatch(List<Runnable> rollbacks) {
            this.rollbacks = rollbacks;
        }

        public synchronized void rollback() {
            if (rolledBack) return;
            RuntimeException failure = null;
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try {
                    rollbacks.get(index).run();
                } catch (RuntimeException error) {
                    if (failure == null) failure = new IllegalStateException("One or more reversible rewards could not be rolled back");
                    failure.addSuppressed(error);
                }
            }
            rolledBack = true;
            if (failure != null) throw failure;
        }
    }

    private record CommandHandler(JavaPlugin plugin) implements RewardHandler {
        @Override public RewardType type() { return RewardType.COMMAND; }
        @Override public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            for (String configured : reward.commands()) {
                String command = sanitizeCommand(TextFormatter.replaceRaw(configured, placeholders), rank);
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Reward command was not handled: " + command.split(" ")[0]));
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private record PermissionHandler(LuckPermsHook hook) implements RewardHandler {
        @Override public RewardType type() { return RewardType.PERMISSION; }
        @Override public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            return hook.addPermissions(player.getUniqueId(), reward.permissions());
        }
    }

    private record GroupHandler(LuckPermsHook hook) implements RewardHandler {
        @Override public RewardType type() { return RewardType.LUCKPERMS_GROUP; }
        @Override public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            return hook.addGroup(player.getUniqueId(), reward.string("group", ""), reward.string("mode", "ADD"));
        }
    }

    private record MoneyHandler(VaultHook hook) implements RewardHandler {
        @Override public RewardType type() { return RewardType.MONEY; }
        @Override public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            var economy = hook.economy().orElseThrow(() -> new IllegalStateException("Vault economy is unavailable"));
            var response = economy.depositPlayer(player, reward.number("amount", 0));
            return response.transactionSuccess() ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(new IllegalStateException("Vault deposit failed: " + response.errorMessage));
        }
    }

    private static final class XpHandler implements RewardHandler {
        @Override public RewardType type() { return RewardType.XP_LEVELS; }
        @Override public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            player.giveExpLevels(Math.max(0, reward.integer("amount", 0)));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class ItemHandler implements RewardHandler {
        private final Supplier<TextFormatter> ignoredFormatter;
        private ItemHandler(Supplier<TextFormatter> formatter) { this.ignoredFormatter = formatter; }
        @Override public RewardType type() { return RewardType.ITEM; }
        @Override public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            ItemStack[] before = cloneArray(player.getInventory().getStorageContents());
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(buildItem(reward, placeholders));
            if (!overflow.isEmpty()) {
                player.getInventory().setStorageContents(before);
                return CompletableFuture.failedFuture(new IllegalStateException("Item reward overflow; world-drop fallback is prohibited"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
