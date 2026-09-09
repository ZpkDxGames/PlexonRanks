package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RankupService;
import com.zpkdxgames.plexonranks.service.RenderService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RankListMenu implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final RankupService rankup;
    private final RenderService render;
    private final Set<UUID> activeViewers = new LinkedHashSet<>();
    private BukkitTask refreshTask;

    public RankListMenu(JavaPlugin plugin, ConfigManager configs, RankService ranks,
                        RankupService rankup, RenderService render) {
        this.plugin = plugin;
        this.configs = configs;
        this.ranks = ranks;
        this.rankup = rankup;
        this.render = render;
    }

    public void open(Player player, int requestedPage) {
        if (!ranks.loaded(player.getUniqueId())) {
            ranks.load(player.getUniqueId());
            return;
        }
        List<Integer> slots = rankSlots();
        List<Rank> visible = visibleRanks(player);
        int pages = Math.max(1, (int) Math.ceil(visible.size() / (double) slots.size()));
        int page = Math.max(1, Math.min(pages, requestedPage));
        int size = configs.current().menus().getInt("rank-list.size", 54);
        Map<String, String> titleValues = Map.of("page", String.valueOf(page), "pages", String.valueOf(pages));
        RankListHolder holder = new RankListHolder(page, pages);
        Inventory inventory = Bukkit.createInventory(holder, size,
                configs.formatter().component(configs.current().menus().getString("rank-list.title", "Plexon Ranks"), titleValues));
        holder.attach(inventory);
        draw(player, holder, inventory, visible, slots);
        player.openInventory(inventory);
        activeViewers.add(player.getUniqueId());
        ensureRefreshTask();
    }

    private void draw(Player player, RankListHolder holder, Inventory inventory, List<Rank> visible, List<Integer> slots) {
        fill(inventory);
        holder.clearRanks();
        List<Integer> usableSlots = slots.stream()
                .filter(slot -> slot >= 0 && slot < inventory.getSize())
                .toList();
        int pages = usableSlots.isEmpty() ? 1
                : Math.max(1, (int) Math.ceil(visible.size() / (double) usableSlots.size()));
        int page = Math.max(1, Math.min(pages, holder.page()));
        holder.pagination(page, pages);
        Rank current = ranks.current(player.getUniqueId()).orElse(configs.current().registry().defaultRank());
        Optional<Rank> next = configs.current().registry().nextAccessible(current, player::hasPermission);
        int offset = (holder.page() - 1) * usableSlots.size();
        for (int index = 0; index < usableSlots.size() && offset + index < visible.size(); index++) {
            Rank rank = visible.get(offset + index);
            RankState state = state(current, next, rank);
            int slot = usableSlots.get(index);
            inventory.setItem(slot, rankItem(player, current, rank, state));
            holder.rank(slot, rank.id(), state);
        }
        navigation(player, current, next, holder, inventory);
    }

    private ItemStack rankItem(Player player, Rank current, Rank rank, RankState state) {
        String statePath = "rank-list.states." + state.name().toLowerCase();
        String material = rank.menu().material().isBlank()
                ? configs.current().menus().getString(statePath + ".material", "PAPER")
                : rank.menu().material();
        boolean glow = rank.menu().glow() || configs.current().menus().getBoolean(statePath + ".glow", false);
        String name = rank.menu().name().isBlank()
                ? configs.current().menus().getString("rank-list.rank-template.name", "%rank_name%")
                : rank.menu().name();
        List<String> lore = rank.menu().useGlobalTemplate() || rank.menu().lore().isEmpty()
                ? configs.current().menus().getStringList("rank-list.rank-template.lore")
                : rank.menu().lore();
        List<RequirementProgress> progress = render.progress(player, rank);
        Map<String, String> values = render.placeholders(player, current, rank, progress, state);
        List<String> expanded = render.expand(lore, rank, state,
                render.requirementLines(progress), render.rewardLines(rank));
        return MenuItems.create(configs.formatter(), material, rank.menu().amount(), name, expanded, glow,
                rank.menu().customModelData(), values);
    }

    private void navigation(Player player, Rank current, Optional<Rank> next, RankListHolder holder, Inventory inventory) {
        Map<String, String> values = new LinkedHashMap<>(render.placeholders(player, current, next));
        values.put("page", String.valueOf(holder.page()));
        values.put("pages", String.valueOf(holder.pages()));
        values.put("previous_page", String.valueOf(Math.max(1, holder.page() - 1)));
        values.put("next_page", String.valueOf(Math.min(holder.pages(), holder.page() + 1)));
        if (holder.page() > 1) setNavigation(inventory, "previous", values);
        setNavigation(inventory, "info", values);
        setNavigation(inventory, "close", values);
        if (holder.page() < holder.pages()) setNavigation(inventory, "next", values);
    }

    private void setNavigation(Inventory inventory, String key, Map<String, String> values) {
        String path = "rank-list.navigation." + key;
        ConfigurationSection section = configs.current().menus().getConfigurationSection(path);
        if (section == null) return;
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, MenuItems.create(configs.formatter(), section.getString("material", "PAPER"), 1,
                section.getString("name", key), section.getStringList("lore"), section.getBoolean("glow", false),
                section.getInt("custom-model-data", 0), values));
    }

    private void fill(Inventory inventory) {
        if (!configs.current().menus().getBoolean("rank-list.filler.enabled", true)) {
            inventory.clear();
            return;
        }
        ItemStack filler = MenuItems.create(configs.formatter(),
                configs.current().menus().getString("rank-list.filler.material", "GRAY_STAINED_GLASS_PANE"), 1,
                configs.current().menus().getString("rank-list.filler.name", " "), List.of(), false, 0, Map.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RankListHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        String rankId = holder.rankAt(slot);
        if (rankId != null) {
            if (holder.stateAt(slot) == RankState.NEXT && event.isRightClick()
                    && configs.current().config().getBoolean("rankup.right-click-next-rank", true)) {
                player.closeInventory();
                rankup.attempt(player);
            }
            return;
        }
        int previous = configs.current().menus().getInt("rank-list.navigation.previous.slot", 45);
        int next = configs.current().menus().getInt("rank-list.navigation.next.slot", 53);
        int close = configs.current().menus().getInt("rank-list.navigation.close.slot", 50);
        if (slot == previous && holder.page() > 1) open(player, holder.page() - 1);
        else if (slot == next && holder.page() < holder.pages()) open(player, holder.page() + 1);
        else if (slot == close) player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RankListHolder)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RankListHolder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof RankListHolder)) {
                activeViewers.remove(player.getUniqueId());
                stopRefreshWhenUnused();
            }
        });
    }

    public void stop() {
        activeViewers.clear();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void ensureRefreshTask() {
        if (!configs.current().menus().getBoolean("rank-list.refresh.enabled", true) || refreshTask != null
                || activeViewers.isEmpty()) return;
        long interval = Math.max(20L, configs.current().menus().getLong("rank-list.refresh.interval-ticks", 40L));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID viewerId : new ArrayList<>(activeViewers)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer == null || !viewer.isOnline()) {
                    activeViewers.remove(viewerId);
                    continue;
                }
                Inventory top = viewer.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof RankListHolder holder) {
                    draw(viewer, holder, top, visibleRanks(viewer), rankSlots());
                } else {
                    activeViewers.remove(viewerId);
                }
            }
            stopRefreshWhenUnused();
        }, interval, interval);
    }

    private void stopRefreshWhenUnused() {
        if (refreshTask != null && activeViewers.isEmpty()) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private List<Integer> rankSlots() {
        List<Integer> slots = configs.current().menus().getIntegerList("rank-list.rank-slots");
        return slots.isEmpty() ? List.of(10, 11, 12, 13, 14, 15, 16) : slots;
    }

    private List<Rank> visibleRanks(Player player) {
        return configs.current().registry().visible().stream()
                .filter(rank -> rank.bypassPermission().isBlank() || player.hasPermission(rank.bypassPermission()))
                .toList();
    }

    private RankState state(Rank current, Optional<Rank> next, Rank rank) {
        if (rank.id().equals(current.id())) return next.isEmpty() ? RankState.MAX : RankState.CURRENT;
        if (rank.order() < current.order()) return RankState.COMPLETED;
        if (next.map(value -> value.id().equals(rank.id())).orElse(false)) return RankState.NEXT;
        return RankState.LOCKED;
    }
}
