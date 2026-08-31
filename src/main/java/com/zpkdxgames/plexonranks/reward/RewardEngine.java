package com.zpkdxgames.plexonranks.reward;

import com.zpkdxgames.plexonranks.integration.LuckPermsHook;
import com.zpkdxgames.plexonranks.integration.VaultHook;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankRegistry;
import com.zpkdxgames.plexonranks.model.RewardDefinition;
import com.zpkdxgames.plexonranks.model.RewardType;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import net.kyori.adventure.text.Component;
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
    private final LuckPermsHook luckPerms;
    private final Map<RewardType, RewardHandler> handlers = new EnumMap<>(RewardType.class);

    public RewardEngine(JavaPlugin plugin, VaultHook vault, LuckPermsHook luckPerms, Supplier<TextFormatter> formatter) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
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

    public CompletableFuture<Void> reconcile(UUID uuid, Rank current, RankRegistry registry, boolean cumulative) {
        Set<String> permissions = new LinkedHashSet<>();
        List<RewardDefinition> groups = new ArrayList<>();
        for (Rank rank : registry.ordered()) {
            if (!cumulative && !rank.id().equals(current.id())) {
                continue;
            }
            if (rank.order() > current.order()) {
                break;
            }
            for (RewardDefinition reward : rank.rewards()) {
                if (!reward.persistent()) {
                    continue;
                }
                if (reward.type() == RewardType.PERMISSION) {
                    permissions.addAll(reward.permissions());
                } else if (reward.type() == RewardType.LUCKPERMS_GROUP) {
                    groups.add(reward);
                }
            }
        }
        CompletableFuture<Void> chain = luckPerms.addPermissions(uuid, permissions);
        for (RewardDefinition group : groups) {
            chain = chain.thenCompose(ignored -> luckPerms.addGroup(uuid,
                    group.string("group", ""), group.string("mode", "ADD")));
        }
        return chain;
    }

    public static List<String> display(Rank rank) {
        return rank.rewards().stream().flatMap(reward -> reward.display().stream()).toList();
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

    private record CommandHandler(JavaPlugin plugin) implements RewardHandler {
        @Override
        public RewardType type() {
            return RewardType.COMMAND;
        }

        @Override
        public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            for (String configured : reward.commands()) {
                String command = TextFormatter.replaceRaw(configured, placeholders).strip();
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                if (command.contains("\n") || command.contains("\r") || command.isBlank()) {
                    throw new IllegalArgumentException("Unsafe or empty reward command for rank " + rank.id());
                }
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    plugin.getLogger().warning("Reward command for " + rank.id() + " was not handled: " + command.split(" ")[0]);
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private record PermissionHandler(LuckPermsHook hook) implements RewardHandler {
        @Override
        public RewardType type() {
            return RewardType.PERMISSION;
        }

        @Override
        public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            return hook.addPermissions(player.getUniqueId(), reward.permissions());
        }
    }

    private record GroupHandler(LuckPermsHook hook) implements RewardHandler {
        @Override
        public RewardType type() {
            return RewardType.LUCKPERMS_GROUP;
        }

        @Override
        public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            return hook.addGroup(player.getUniqueId(), reward.string("group", ""), reward.string("mode", "ADD"));
        }
    }

    private record MoneyHandler(VaultHook hook) implements RewardHandler {
        @Override
        public RewardType type() {
            return RewardType.MONEY;
        }

        @Override
        public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            var economy = hook.economy().orElseThrow(() -> new IllegalStateException("Vault economy is unavailable"));
            double amount = reward.number("amount", 0);
            var response = economy.depositPlayer(player, amount);
            if (!response.transactionSuccess()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Vault deposit failed: " + response.errorMessage));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class XpHandler implements RewardHandler {
        @Override
        public RewardType type() {
            return RewardType.XP_LEVELS;
        }

        @Override
        public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            player.giveExpLevels(Math.max(0, reward.integer("amount", 0)));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ItemHandler implements RewardHandler {
        private final Supplier<TextFormatter> formatter;

        private ItemHandler(Supplier<TextFormatter> formatter) {
            this.formatter = formatter;
        }

        @Override
        public RewardType type() {
            return RewardType.ITEM;
        }

        @Override
        public CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders) {
            Material material = Material.matchMaterial(reward.string("material", "STONE"));
            if (material == null || material.isAir()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid item reward material"));
            }
            ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, reward.integer("amount", 1))));
            ItemMeta meta = item.getItemMeta();
            String name = reward.string("name", "");
            if (!name.isBlank()) {
                meta.displayName(formatter.get().component(name, placeholders));
            }
            Object loreValue = reward.options().get("lore");
            if (loreValue instanceof List<?> lore) {
                List<Component> components = lore.stream()
                        .map(String::valueOf)
                        .map(line -> formatter.get().component(line, placeholders))
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
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            return CompletableFuture.completedFuture(null);
        }
    }
}
