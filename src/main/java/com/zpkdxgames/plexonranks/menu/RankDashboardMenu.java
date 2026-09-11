package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.event.PlexonRankupEvent;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankHistoryEntry;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.service.MessageService;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RankupService;
import com.zpkdxgames.plexonranks.service.RenderService;
import com.zpkdxgames.plexonranks.util.NumberFormats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Phase 3 player progression product. Phase 2 services remain all mutation authorities. */
public final class RankDashboardMenu implements Listener {
    private static final int SIZE = 54, BACK = 48, PRIMARY = 49, SECONDARY = 50, STATUS = 51, CLOSE = 52;
    private static final List<Integer> BODY = List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43);
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final RankupService rankup;
    private final RenderService render;
    private final MessageService messages;
    private final RankListMenu pathMenu;
    private final Set<UUID> submitting = new HashSet<>();

    public RankDashboardMenu(JavaPlugin plugin, ConfigManager configs, RankService ranks, RankupService rankup,
                             RenderService render, MessageService messages, RankListMenu pathMenu) {
        this.plugin = plugin; this.configs = configs; this.ranks = ranks; this.rankup = rankup;
        this.render = render; this.messages = messages; this.pathMenu = pathMenu;
    }

    public void open(Player player) {
        RankDashboardViewModel model = loadedModel(player); if (model == null) return;
        Inventory inv = create(model, RankDashboardHolder.View.ROOT, "<gradient:#4158D0:#C850C0><bold>RANK DASHBOARD</bold></gradient>");
        inv.setItem(4, profile(player, model));
        inv.setItem(20, item("BOOK", "<aqua><bold>CURRENT RANK</bold></aqua>", List.of(
                "<!italic>" + model.current().display().name(), "<!italic><gray>Tier</gray> <dark_gray>›</dark_gray> <white>" + model.current().tier() + "</white>"), true));
        inv.setItem(22, nextItem(model));
        inv.setItem(24, item("WRITABLE_BOOK", "<yellow><bold>REQUIREMENTS</bold></yellow>", requirementSummary(model), false));
        inv.setItem(29, item("CHEST", "<green><bold>REWARDS</bold></green>", rewardSummary(model), false));
        inv.setItem(31, item("COMPASS", "<aqua><bold>PROGRESSION PATH</bold></aqua>", List.of("<!italic><gray>Completed • Current • Next • Locked</gray>", "<!italic><aqua>Click for the full path and rank details.</aqua>"), false));
        inv.setItem(33, item("CLOCK", "<light_purple><bold>HISTORY</bold></light_purple>", List.of("<!italic><gray>Recent progression changes.</gray>", "<!italic><dark_gray>Loads without blocking this menu.</dark_gray>"), false));
        inv.setItem(38, item("EXPERIENCE_BOTTLE", "<blue><bold>STATISTICS</bold></blue>", List.of("<!italic><gray>Journey</gray> <dark_gray>›</dark_gray> <white>" + journeyPercent(model.current()) + "%</white>"), false));
        inv.setItem(40, item("KNOWLEDGE_BOOK", "<white><bold>HELP</bold></white>", List.of("<!italic><gray>Understand progression and the explicit next action.</gray>"), false));
        controls(inv, model, false);
        player.openInventory(inv);
    }

    public void openRequirements(Player player) {
        RankDashboardViewModel model = loadedModel(player); if (model == null) return;
        Inventory inv = create(model, RankDashboardHolder.View.REQUIREMENTS, "<yellow><bold>NEXT RANK REQUIREMENTS</bold></yellow>");
        inv.setItem(4, header(model, "REQUIREMENTS"));
        if (model.maximum()) inv.setItem(22, item("NETHER_STAR", "<gold><bold>MASTERY COMPLETE</bold></gold>", List.of("<!italic><gray>No next-rank requirements remain.</gray>"), true));
        else for (int i = 0; i < Math.min(BODY.size(), model.requirementCards().size()); i++) inv.setItem(BODY.get(i), requirementItem(model.requirementCards().get(i)));
        controls(inv, model, true); player.openInventory(inv);
    }

    public void openRewards(Player player) {
        RankDashboardViewModel model = loadedModel(player); if (model == null) return;
        Inventory inv = create(model, RankDashboardHolder.View.REWARDS, "<green><bold>NEXT RANK REWARDS</bold></green>");
        inv.setItem(4, header(model, "REWARDS"));
        if (model.maximum()) inv.setItem(22, item("NETHER_STAR", "<gold><bold>MASTERY COMPLETE</bold></gold>", List.of("<!italic><gray>No future rank rewards remain.</gray>"), true));
        else for (int i = 0; i < Math.min(BODY.size(), model.rewards().size()); i++) inv.setItem(BODY.get(i), item("EMERALD", "<green><bold>REWARD</bold></green>", List.of(model.rewards().get(i)), false));
        controls(inv, model, true); player.openInventory(inv);
    }

    public void openHistory(Player player) {
        RankDashboardViewModel model = loadedModel(player); if (model == null) return;
        Inventory inv = create(model, RankDashboardHolder.View.HISTORY, "<light_purple><bold>RANK HISTORY</bold></light_purple>");
        RankDashboardHolder holder = (RankDashboardHolder) inv.getHolder(); UUID requestId = holder.requestId();
        inv.setItem(4, header(model, "HISTORY"));
        inv.setItem(22, item("CLOCK", "<yellow><bold>LOADING</bold></yellow>", List.of("<!italic><gray>Loading recent rank history…</gray>"), false));
        controls(inv, model, true); player.openInventory(inv);
        ranks.history(player.getUniqueId(), 21).whenComplete((history, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            Inventory current = player.getOpenInventory().getTopInventory();
            if (current != inv) return;
            if (!(current.getHolder() instanceof RankDashboardHolder active) || active.view() != RankDashboardHolder.View.HISTORY || !active.requestId().equals(requestId)) return;
            renderHistory(inv, history, error);
        }));
    }

    public void openStatistics(Player player) {
        RankDashboardViewModel model = loadedModel(player); if (model == null) return;
        Inventory inv = create(model, RankDashboardHolder.View.STATISTICS, "<blue><bold>PROGRESSION STATISTICS</bold></blue>");
        inv.setItem(20, item("GOLD_INGOT", "<gold><bold>JOURNEY</bold></gold>", List.of(
                "<!italic><gray>Position</gray> <dark_gray>›</dark_gray> <white>" + journeyPosition(model.current()) + "</white>",
                "<!italic><gray>Overall path</gray> <dark_gray>›</dark_gray> <white>" + journeyPercent(model.current()) + "%</white>"), false));
        inv.setItem(24, item("EXPERIENCE_BOTTLE", "<aqua><bold>NEXT-RANK READINESS</bold></aqua>", List.of(
                "<!italic><gray>Requirements</gray> <dark_gray>›</dark_gray> <white>" + model.requirementsComplete() + "/" + model.requirements().size() + "</white>",
                "<!italic>" + render.progressBar(model.readiness())), model.readyToRankUp() || model.maximum()));
        controls(inv, model, true); player.openInventory(inv);
    }

    public void openHelp(Player player) {
        RankDashboardViewModel model = loadedModel(player); if (model == null) return;
        Inventory inv = create(model, RankDashboardHolder.View.HELP, "<white><bold>PLEXONRANKS HELP</bold></white>");
        inv.setItem(20, item("BOOK", "<white><bold>PROGRESSION STATES</bold></white>", List.of(
                "<!italic><green>✔ COMPLETED</green>", "<!italic><aqua>◆ CURRENT RANK</aqua>", "<!italic><yellow>➜ NEXT RANK</yellow>", "<!italic><red>✖ LOCKED</red>"), false));
        inv.setItem(24, item("LIME_CONCRETE", "<green><bold>RANK UP</bold></green>", List.of(
                "<!italic><gray>When every requirement is ready, use the labelled action.</gray>", "<!italic><gray>No hidden right-click is required.</gray>"), false));
        controls(inv, model, true); player.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RankDashboardHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= SIZE) return;
        int slot = event.getRawSlot();
        if (slot == CLOSE) { player.closeInventory(); return; }
        if (slot == PRIMARY) { handlePrimary(player, holder); return; }
        if (slot == SECONDARY) { if (holder.view() == RankDashboardHolder.View.ROOT) openHistory(player); else pathMenu.openAround(player, current(player).id()); return; }
        if (holder.view() != RankDashboardHolder.View.ROOT) { if (slot == BACK) open(player); return; }
        switch (slot) {
            case 22, 24 -> openRequirements(player); case 29 -> openRewards(player); case 31, BACK -> pathMenu.openAround(player, current(player).id());
            case 33 -> openHistory(player); case 38 -> openStatistics(player); case 40 -> openHelp(player); default -> { }
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RankDashboardHolder && event.getRawSlots().stream().anyMatch(slot -> slot < SIZE)) event.setCancelled(true);
    }

    @EventHandler public void onRankup(PlexonRankupEvent event) {
        Player player = event.getPlayer(); if (!player.isOnline()) return;
        Optional<Rank> next = configs.current().registry().nextAccessible(event.to(), player::hasPermission);
        player.sendActionBar(configs.formatter().component("<green><bold>RANKED UP</bold></green> <dark_gray>•</dark_gray> " + event.to().display().name()
                + next.map(rank -> " <dark_gray>•</dark_gray> <gray>Next:</gray> " + rank.display().name()).orElse(" <dark_gray>•</dark_gray> <gold>Progression mastered</gold>")));
        List<String> rewards = RankPresentation.rewards(event.to());
        if (!event.to().rewards().isEmpty()) player.sendMessage(configs.formatter().component("<gray>Rewards:</gray> " + String.join(" <dark_gray>•</dark_gray> ", rewards.subList(0, Math.min(3, rewards.size())))));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { submitting.remove(event.getPlayer().getUniqueId()); }

    private void handlePrimary(Player player, RankDashboardHolder holder) {
        RankDashboardViewModel model = model(player);
        if (!holder.matches(model) || holder.primaryAction() != model.primaryAction()) { player.sendActionBar(configs.formatter().component("<yellow>Your rank progress changed. The dashboard was refreshed.</yellow>")); open(player); return; }
        switch (model.primaryAction()) {
            case VIEW_PROGRESSION -> pathMenu.openAround(player, model.current().id());
            case VIEW_REQUIREMENTS -> openRequirements(player);
            case RANK_UP -> submitRankup(player, holder, model);
        }
    }

    private void submitRankup(Player player, RankDashboardHolder holder, RankDashboardViewModel model) {
        UUID id = player.getUniqueId(); if (!holder.matches(model) || !model.readyToRankUp() || !submitting.add(id)) return;
        player.closeInventory(); try { rankup.attempt(player); } finally { submitting.remove(id); }
    }

    private void renderHistory(Inventory inv, List<RankHistoryEntry> history, Throwable error) {
        for (int slot : BODY) inv.setItem(slot, pane("BLACK_STAINED_GLASS_PANE"));
        if (error != null) { inv.setItem(22, item("BARRIER", "<red><bold>HISTORY UNAVAILABLE</bold></red>", List.of("<!italic><gray>Your history could not be loaded right now.</gray>"), false)); return; }
        if (history == null || history.isEmpty()) { inv.setItem(22, item("PAPER", "<gray><bold>NO HISTORY YET</bold></gray>", List.of("<!italic><gray>Your completed rank changes will appear here.</gray>"), false)); return; }
        for (int i = 0; i < Math.min(BODY.size(), history.size()); i++) {
            RankHistoryEntry e = history.get(i);
            String from = RankPresentation.rankName(configs.current().registry(), e.fromRank(), "<gray>Previous rank</gray>");
            String to = RankPresentation.rankName(configs.current().registry(), e.toRank(), "<gray>Updated rank</gray>");
            inv.setItem(BODY.get(i), item(historyMaterial(e.status()), from + " <dark_gray>→</dark_gray> " + to, List.of(
                    "<!italic><gray>When</gray> <dark_gray>›</dark_gray> <white>" + HISTORY_TIME.format(e.createdAt()) + "</white>",
                    "<!italic><gray>Reason</gray> <dark_gray>›</dark_gray> <white>" + RankPresentation.historyCause(e.cause()) + "</white>",
                    "<!italic><gray>Result</gray> <dark_gray>›</dark_gray> <white>" + RankPresentation.historyStatus(e.status()) + "</white>"), "COMPLETED".equalsIgnoreCase(e.status())));
        }
    }

    private RankDashboardViewModel loadedModel(Player player) {
        if (!ranks.loaded(player.getUniqueId())) { ranks.load(player.getUniqueId()); messages.send(player, "generic.data-loading"); return null; }
        return model(player);
    }

    private RankDashboardViewModel model(Player player) {
        Rank current = current(player); Optional<Rank> next = configs.current().registry().nextAccessible(current, player::hasPermission);
        List<RequirementProgress> progress = next.map(rank -> render.progress(player, rank)).orElseGet(List::of);
        return RankDashboardViewModel.create(current, next, progress, next.map(RankPresentation::rewards).orElseGet(List::of));
    }

    private Rank current(Player player) { return ranks.current(player.getUniqueId()).orElse(configs.current().registry().defaultRank()); }

    private Inventory create(RankDashboardViewModel model, RankDashboardHolder.View view, String title) {
        RankDashboardHolder holder = new RankDashboardHolder(view, model); Inventory inv = Bukkit.createInventory(holder, SIZE, configs.formatter().component(title)); holder.attach(inv); fill(inv); return inv;
    }

    private void controls(Inventory inv, RankDashboardViewModel model, boolean back) {
        inv.setItem(BACK, item(back ? "ARROW" : "COMPASS", back ? "<yellow><bold>BACK TO DASHBOARD</bold></yellow>" : "<aqua><bold>PROGRESSION PATH</bold></aqua>", List.of(), false));
        inv.setItem(PRIMARY, primaryItem(model));
        inv.setItem(SECONDARY, item(back ? "COMPASS" : "CLOCK", back ? "<aqua><bold>PROGRESSION PATH</bold></aqua>" : "<light_purple><bold>HISTORY</bold></light_purple>", List.of(), false));
        inv.setItem(STATUS, statusItem(model)); inv.setItem(CLOSE, item("BARRIER", "<red><bold>CLOSE</bold></red>", List.of(), false));
    }

    private ItemStack profile(Player player, RankDashboardViewModel model) {
        ItemStack head = item("PLAYER_HEAD", "<white><bold>" + player.getName() + "</bold></white>", List.of(
                "<!italic><gray>Current Rank</gray> <dark_gray>›</dark_gray> " + model.current().display().name(),
                "<!italic><gray>Next Rank</gray> <dark_gray>›</dark_gray> " + model.next().map(r -> r.display().name()).orElse("<gold>MASTERED</gold>"),
                "<!italic>" + render.progressBar(model.readiness()) + " <white>" + NumberFormats.number(model.readiness() * 100) + "%</white>"), false);
        if (head.getItemMeta() instanceof SkullMeta skull) { skull.setOwningPlayer(player); head.setItemMeta(skull); } return head;
    }

    private ItemStack nextItem(RankDashboardViewModel model) {
        if (model.maximum()) return item("NETHER_STAR", "<gold><bold>MAXIMUM RANK • MASTERED</bold></gold>", List.of("<!italic><gold>★ Full progression complete.</gold>"), true);
        return item(model.readyToRankUp() ? "LIME_CONCRETE" : "YELLOW_CONCRETE", model.readyToRankUp() ? "<green><bold>READY TO RANK UP</bold></green>" : "<yellow><bold>NEXT RANK</bold></yellow>", List.of(
                "<!italic>" + model.next().orElseThrow().display().name(), "<!italic><gray>Requirements</gray> <dark_gray>›</dark_gray> <white>" + model.requirementsComplete() + "/" + model.requirements().size() + "</white>",
                model.readyToRankUp() ? "<!italic><green>All requirements complete.</green>" : "<!italic><yellow>Next: " + model.blocker() + "</yellow>"), model.readyToRankUp());
    }

    private List<String> requirementSummary(RankDashboardViewModel model) {
        if (model.maximum()) return List.of("<!italic><gold>All progression requirements are complete.</gold>");
        List<String> out = new ArrayList<>(List.of("<!italic><gray>Completed</gray> <dark_gray>›</dark_gray> <white>" + model.requirementsComplete() + "/" + model.requirements().size() + "</white>"));
        if (model.requirementsRemaining() > 0) { out.add("<!italic><yellow>Next: " + model.blocker() + "</yellow>"); if (model.requirementsRemaining() > 1) out.add("<!italic><dark_gray>+" + (model.requirementsRemaining() - 1) + " more requirements</dark_gray>"); }
        out.add("<!italic><yellow>Click for full details.</yellow>"); return List.copyOf(out);
    }

    private List<String> rewardSummary(RankDashboardViewModel model) {
        if (model.maximum()) return List.of("<!italic><gray>No future rewards remain.</gray>");
        List<String> out = new ArrayList<>(); for (int i = 0; i < Math.min(3, model.rewards().size()); i++) out.add(model.rewards().get(i));
        if (model.rewards().size() > 3) out.add("<!italic><dark_gray>+" + (model.rewards().size() - 3) + " more rewards</dark_gray>"); out.add("<!italic><green>Click for the full reward preview.</green>"); return List.copyOf(out);
    }

    private ItemStack requirementItem(RankPresentation.RequirementCard card) {
        List<String> lore = new ArrayList<>(List.of(card.complete() ? "<!italic><green>✔ COMPLETE</green>" : "<!italic><yellow>◆ IN PROGRESS</yellow>",
                "<!italic><gray>Current</gray> <dark_gray>›</dark_gray> <white>" + card.current() + "</white>", "<!italic><gray>Required</gray> <dark_gray>›</dark_gray> <white>" + card.required() + "</white>"));
        if (card.percentageBased()) lore.add("<!italic>" + render.progressBar(card.normalized()) + " <white>" + NumberFormats.number(card.normalized() * 100) + "%</white>");
        if (!card.complete()) lore.add("<!italic><yellow>Next: " + card.nextStep() + "</yellow>");
        String material = switch (card.type()) { case MONEY -> "GOLD_INGOT"; case XP_LEVELS -> "EXPERIENCE_BOTTLE"; case PLAYTIME -> "CLOCK"; case PERMISSION -> "NAME_TAG"; case PLACEHOLDER -> "COMPASS"; case ITEM -> "CHEST"; };
        return item(material, (card.complete() ? "<green><bold>" : "<yellow><bold>") + card.title() + "</bold>" + (card.complete() ? "</green>" : "</yellow>"), lore, card.complete());
    }

    private ItemStack header(RankDashboardViewModel model, String section) { return item("NETHER_STAR", "<gradient:#4158D0:#C850C0><bold>" + section + "</bold></gradient>", List.of("<!italic><gray>Current Rank</gray> <dark_gray>›</dark_gray> " + model.current().display().name(), "<!italic><gray>Next Rank</gray> <dark_gray>›</dark_gray> " + model.next().map(r -> r.display().name()).orElse("<gold>MASTERED</gold>")), false); }
    private ItemStack primaryItem(RankDashboardViewModel model) { return switch (model.primaryAction()) { case VIEW_PROGRESSION -> item("NETHER_STAR", "<gold><bold>VIEW MASTERY</bold></gold>", List.of(), true); case VIEW_REQUIREMENTS -> item("WRITABLE_BOOK", "<yellow><bold>VIEW REQUIREMENTS</bold></yellow>", List.of("<!italic><gray>Next: " + model.blocker() + "</gray>"), false); case RANK_UP -> item("LIME_CONCRETE", "<green><bold>RANK UP</bold></green>", List.of("<!italic><gray>Advance to</gray> " + model.next().orElseThrow().display().name(), "<!italic><green><bold>CLICK TO RANK UP</bold></green>"), true); }; }
    private ItemStack statusItem(RankDashboardViewModel model) { if (model.maximum()) return item("NETHER_STAR", "<gold><bold>MASTERED</bold></gold>", List.of(), true); if (model.readyToRankUp()) return item("LIME_DYE", "<green><bold>READY TO RANK UP</bold></green>", List.of(), true); return item("YELLOW_DYE", "<yellow><bold>IN PROGRESS</bold></yellow>", List.of("<!italic><gray>Next</gray> <dark_gray>›</dark_gray> <white>" + model.blocker() + "</white>"), false); }
    private String historyMaterial(String status) { String value = status == null ? "" : status.toUpperCase(java.util.Locale.ROOT); return value.equals("COMPLETED") ? "LIME_DYE" : value.startsWith("ROLLED_BACK") ? "ORANGE_DYE" : value.equals("INTERRUPTED") || value.equals("EXTERNAL_REWARD_FAILED") ? "YELLOW_DYE" : "PAPER"; }
    private String journeyPosition(Rank rank) { return (Math.max(0, configs.current().registry().position(rank)) + 1) + " / " + configs.current().registry().ordered().size(); }
    private String journeyPercent(Rank rank) { int max = Math.max(1, configs.current().registry().ordered().size() - 1); return NumberFormats.number(Math.max(0, Math.min(1, configs.current().registry().position(rank) / (double) max)) * 100); }
    private void fill(Inventory inv) { for (int slot = 0; slot < SIZE; slot++) { int row = slot / 9, col = slot % 9; inv.setItem(slot, pane(row == 0 || row == 5 || col == 0 || col == 8 ? "GRAY_STAINED_GLASS_PANE" : "BLACK_STAINED_GLASS_PANE")); } }
    private ItemStack pane(String material) { return item(material, "<black> </black>", List.of(), false); }
    private ItemStack item(String material, String name, List<String> lore, boolean glow) { return MenuItems.create(configs.formatter(), material, 1, "<!italic>" + name, lore, glow, 0, Map.of()); }
}
