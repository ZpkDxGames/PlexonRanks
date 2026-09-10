package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankHistoryEntry;
import com.zpkdxgames.plexonranks.model.RankState;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Premium current-rank dashboard. All inventory routing uses this custom holder, never titles. */
public final class RankDashboardMenu implements Listener {
    private static final int SIZE = 54;
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final RankupService rankup;
    private final RenderService render;
    private final MessageService messages;
    private final RankListMenu pathMenu;

    public RankDashboardMenu(JavaPlugin plugin, ConfigManager configs, RankService ranks, RankupService rankup,
                             RenderService render, MessageService messages, RankListMenu pathMenu) {
        this.plugin = plugin;
        this.configs = configs;
        this.ranks = ranks;
        this.rankup = rankup;
        this.render = render;
        this.messages = messages;
        this.pathMenu = pathMenu;
    }

    public void open(Player player) {
        if (!ranks.loaded(player.getUniqueId())) {
            ranks.load(player.getUniqueId());
            messages.send(player, "generic.data-loading");
            return;
        }
        Rank current = current(player);
        Optional<Rank> next = next(player, current);
        List<RequirementProgress> progress = next.map(rank -> render.progress(player, rank)).orElseGet(List::of);
        Inventory inventory = create(player, RankDashboardHolder.View.ROOT, "<gold><bold>Plexon Ranks</bold></gold> <dark_gray>•</dark_gray> <gray>Progression</gray>");

        inventory.setItem(4, profile(player, current, next, progress));
        inventory.setItem(19, item("BOOK", "<gold><bold>Current Rank</bold></gold>", List.of(
                "<!italic><gray>✥ Rank</gray>      " + current.display().name(),
                "<!italic><gray>◆ Tier</gray>      <white>" + current.tier() + "</white>",
                "<!italic><gray>⚡ Progress</gray>  <white>" + percent(progress, next) + "%</white>",
                "<!italic>" + render.progressBar(next.isEmpty() ? 1.0 : overall(progress)),
                "",
                next.map(rank -> "<!italic><gray>Next</gray> <dark_gray>›</dark_gray> " + rank.display().name())
                        .orElse("<!italic><gold>★ Maximum rank mastered</gold>")
        ), true));
        inventory.setItem(21, item("WRITABLE_BOOK", "<yellow><bold>Requirements</bold></yellow>",
                summaryRequirements(progress, next), false));
        inventory.setItem(23, item("CHEST", "<green><bold>Rewards</bold></green>", summaryRewards(next), false));
        inventory.setItem(25, item("COMPASS", "<aqua><bold>Progression Path</bold></aqua>", List.of(
                "<!italic><gray>Completed</gray> <dark_gray>›</dark_gray> <white>" + completedCount(current) + "</white>",
                "<!italic><gray>Total ranks</gray> <dark_gray>›</dark_gray> <white>" + configs.current().registry().ordered().size() + "</white>",
                "",
                "<!italic><aqua>Click to view the full rank path.</aqua>"
        ), false));
        inventory.setItem(30, item("CLOCK", "<light_purple><bold>Rank History</bold></light_purple>", List.of(
                "<!italic><gray>Review recent authoritative rank changes.</gray>",
                "<!italic><dark_gray>Loaded asynchronously from SQLite.</dark_gray>"
        ), false));
        inventory.setItem(32, item("EXPERIENCE_BOTTLE", "<blue><bold>Statistics</bold></blue>", List.of(
                "<!italic><gray>Rank order</gray> <dark_gray>›</dark_gray> <white>" + current.order() + "</white>",
                "<!italic><gray>Journey</gray> <dark_gray>›</dark_gray> <white>" + journeyPercent(current) + "%</white>",
                "<!italic><gray>Requirements complete</gray> <dark_gray>›</dark_gray> <white>" + completeCount(progress) + "/" + progress.size() + "</white>"
        ), false));
        inventory.setItem(34, item("KNOWLEDGE_BOOK", "<gray><bold>Help</bold></gray>", List.of(
                "<!italic><white>/rank</white> <gray>overview</gray>",
                "<!italic><white>/rank info</white> <gray>text report</gray>",
                "<!italic><white>/ranks</white> <gray>progression path</gray>",
                "<!italic><white>/rankup</white> <gray>attempt promotion</gray>"
        ), false));
        inventory.setItem(40, rankupItem(next, progress));
        inventory.setItem(49, item("BARRIER", "<red>Close</red>", List.of("<!italic><dark_gray>Return to the game.</dark_gray>"), false));
        player.openInventory(inventory);
    }

    private void openRequirements(Player player) {
        Rank current = current(player);
        Optional<Rank> next = next(player, current);
        List<RequirementProgress> progress = next.map(rank -> render.progress(player, rank)).orElseGet(List::of);
        Inventory inventory = create(player, RankDashboardHolder.View.REQUIREMENTS, "<yellow><bold>Rank Requirements</bold></yellow>");
        if (next.isEmpty()) {
            inventory.setItem(22, item("NETHER_STAR", "<gold><bold>Mastery Complete</bold></gold>",
                    List.of("<!italic><gray>No next-rank requirements remain.</gray>"), true));
        } else {
            List<String> lines = render.requirementLines(progress);
            for (int i = 0; i < Math.min(lines.size(), 27); i++) {
                int slot = 10 + i + (i / 7) * 2;
                if (slot >= 44) break;
                RequirementProgress value = progress.get(i);
                inventory.setItem(slot, item(value.complete() ? "LIME_DYE" : "ORANGE_DYE",
                        value.complete() ? "<green><bold>Requirement complete</bold></green>" : "<gold><bold>Requirement in progress</bold></gold>",
                        List.of(lines.get(i), "<!italic><gray>Progress</gray> <dark_gray>›</dark_gray> <white>"
                                + NumberFormats.number(value.normalized() * 100.0) + "%</white>"), value.complete()));
            }
        }
        back(inventory);
        player.openInventory(inventory);
    }

    private void openRewards(Player player) {
        Rank current = current(player);
        Optional<Rank> next = next(player, current);
        Inventory inventory = create(player, RankDashboardHolder.View.REWARDS, "<green><bold>Next Rank Rewards</bold></green>");
        List<String> rewards = next.map(render::rewardLines).orElseGet(() -> List.of("<!italic><gray>Maximum rank reached.</gray>"));
        for (int i = 0; i < Math.min(rewards.size(), 21); i++) {
            int slot = 10 + i + (i / 7) * 2;
            if (slot >= 44) break;
            inventory.setItem(slot, item("EMERALD", "<green>Reward</green>", List.of(rewards.get(i)), false));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    private void openHistory(Player player) {
        ranks.history(player.getUniqueId(), 21).whenComplete((history, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    Inventory inventory = create(player, RankDashboardHolder.View.HISTORY, "<light_purple><bold>Rank History</bold></light_purple>");
                    if (error != null) {
                        inventory.setItem(22, item("BARRIER", "<red>History unavailable</red>",
                                List.of("<!italic><gray>SQLite history could not be loaded.</gray>"), false));
                    } else if (history.isEmpty()) {
                        inventory.setItem(22, item("PAPER", "<gray>No rank history yet</gray>",
                                List.of("<!italic><dark_gray>Your next committed change will appear here.</dark_gray>"), false));
                    } else {
                        for (int i = 0; i < Math.min(21, history.size()); i++) {
                            RankHistoryEntry entry = history.get(i);
                            int slot = 10 + i + (i / 7) * 2;
                            if (slot >= 44) break;
                            inventory.setItem(slot, item(statusMaterial(entry.status()), "<white>" + entry.fromRank()
                                    + "</white> <dark_gray>→</dark_gray> <gold>" + entry.toRank() + "</gold>", List.of(
                                    "<!italic><gray>Cause</gray> <dark_gray>›</dark_gray> <white>" + entry.cause() + "</white>",
                                    "<!italic><gray>Status</gray> <dark_gray>›</dark_gray> <white>" + entry.status() + "</white>",
                                    "<!italic><gray>Time</gray> <dark_gray>›</dark_gray> <white>" + HISTORY_TIME.format(entry.createdAt()) + "</white>"
                            ), "COMPLETED".equals(entry.status())));
                        }
                    }
                    back(inventory);
                    player.openInventory(inventory);
                }));
    }

    private void openStatistics(Player player) {
        Rank current = current(player);
        Optional<Rank> next = next(player, current);
        List<RequirementProgress> progress = next.map(rank -> render.progress(player, rank)).orElseGet(List::of);
        Inventory inventory = create(player, RankDashboardHolder.View.STATISTICS, "<blue><bold>Progression Statistics</bold></blue>");
        inventory.setItem(20, item("GOLD_INGOT", "<gold>Journey</gold>", List.of(
                "<!italic><gray>Current order</gray> <dark_gray>›</dark_gray> <white>" + current.order() + "</white>",
                "<!italic><gray>Completed path</gray> <dark_gray>›</dark_gray> <white>" + completedCount(current) + "</white>",
                "<!italic><gray>Overall path</gray> <dark_gray>›</dark_gray> <white>" + journeyPercent(current) + "%</white>"
        ), false));
        inventory.setItem(24, item("EXPERIENCE_BOTTLE", "<aqua>Next-rank readiness</aqua>", List.of(
                "<!italic><gray>Requirements</gray> <dark_gray>›</dark_gray> <white>" + completeCount(progress) + "/" + progress.size() + "</white>",
                "<!italic><gray>Progress</gray> <dark_gray>›</dark_gray> <white>" + percent(progress, next) + "%</white>",
                next.isEmpty() ? "<!italic><gold>Maximum rank reached.</gold>" : "<!italic>" + render.progressBar(overall(progress))
        ), next.isPresent() && completeCount(progress) == progress.size()));
        back(inventory);
        player.openInventory(inventory);
    }

    private void openHelp(Player player) {
        Inventory inventory = create(player, RankDashboardHolder.View.HELP, "<gray><bold>PlexonRanks Help</bold></gray>");
        inventory.setItem(20, item("BOOK", "<white>Understand progression</white>", List.of(
                "<!italic><green>✔</green> <gray>Completed ranks</gray>",
                "<!italic><aqua>◆</aqua> <gray>Current rank</gray>",
                "<!italic><gold>➜</gold> <gray>Next available rank</gray>",
                "<!italic><red>✖</red> <gray>Future locked ranks</gray>"
        ), false));
        inventory.setItem(24, item("ANVIL", "<white>Rank up safely</white>", List.of(
                "<!italic><gray>Meet every requirement, then use</gray>",
                "<!italic><white>/rankup</white> <gray>or the dashboard action.</gray>",
                "<!italic><dark_gray>Consumed requirements are refunded when a reversible transaction stage fails.</dark_gray>"
        ), false));
        back(inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RankDashboardHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        if (slot == 49) { player.closeInventory(); return; }
        if (holder.view() != RankDashboardHolder.View.ROOT && slot == 45) { open(player); return; }
        if (holder.view() != RankDashboardHolder.View.ROOT) return;
        switch (slot) {
            case 21 -> openRequirements(player);
            case 23 -> openRewards(player);
            case 25 -> pathMenu.open(player, 1);
            case 30 -> openHistory(player);
            case 32 -> openStatistics(player);
            case 34 -> openHelp(player);
            case 40 -> {
                player.closeInventory();
                rankup.attempt(player);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RankDashboardHolder)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) event.setCancelled(true);
    }

    private Inventory create(Player player, RankDashboardHolder.View view, String title) {
        RankDashboardHolder holder = new RankDashboardHolder(view);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, configs.formatter().component(title));
        holder.attach(inventory);
        return inventory;
    }

    private ItemStack profile(Player player, Rank current, Optional<Rank> next, List<RequirementProgress> progress) {
        ItemStack head = item("PLAYER_HEAD", "<white><bold>" + player.getName() + "</bold></white>", List.of(
                "<!italic><gray>Current</gray> <dark_gray>›</dark_gray> " + current.display().name(),
                "<!italic><gray>Tier</gray> <dark_gray>›</dark_gray> <white>" + current.tier() + "</white>",
                "<!italic><gray>Next</gray> <dark_gray>›</dark_gray> " + next.map(rank -> rank.display().name()).orElse("<gold>Mastered</gold>"),
                "<!italic><gray>Readiness</gray> <dark_gray>›</dark_gray> <white>" + percent(progress, next) + "%</white>"
        ), false);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            head.setItemMeta(skull);
        }
        return head;
    }

    private ItemStack rankupItem(Optional<Rank> next, List<RequirementProgress> progress) {
        if (next.isEmpty()) return item("NETHER_STAR", "<gold><bold>Maximum Rank</bold></gold>", List.of(
                "<!italic><gold>★ Full progression mastery achieved.</gold>"
        ), true);
        boolean ready = progress.stream().allMatch(RequirementProgress::complete);
        return item(ready ? "LIME_CONCRETE" : "ORANGE_CONCRETE",
                ready ? "<green><bold>Rank Up</bold></green>" : "<gold><bold>Rank Up</bold></gold>", List.of(
                        "<!italic><gray>Target</gray> <dark_gray>›</dark_gray> " + next.get().display().name(),
                        "<!italic><gray>Ready</gray> <dark_gray>›</dark_gray> <white>" + completeCount(progress) + "/" + progress.size() + "</white>",
                        "",
                        ready ? "<!italic><green>Click to attempt promotion.</green>"
                                : "<!italic><gold>Complete every requirement first.</gold>"
                ), ready);
    }

    private List<String> summaryRequirements(List<RequirementProgress> progress, Optional<Rank> next) {
        if (next.isEmpty()) return List.of("<!italic><gold>All progression requirements are complete.</gold>");
        List<String> lines = new ArrayList<>();
        lines.add("<!italic><gray>Complete</gray> <dark_gray>›</dark_gray> <white>" + completeCount(progress) + "/" + progress.size() + "</white>");
        lines.add("<!italic><gray>Readiness</gray> <dark_gray>›</dark_gray> <white>" + percent(progress, next) + "%</white>");
        lines.add("");
        lines.add("<!italic><yellow>Click for full requirement progress.</yellow>");
        return List.copyOf(lines);
    }

    private List<String> summaryRewards(Optional<Rank> next) {
        if (next.isEmpty()) return List.of("<!italic><gray>No future rewards remain.</gray>");
        List<String> rewards = render.rewardLines(next.get());
        List<String> lines = new ArrayList<>();
        lines.add("<!italic><gray>Next rank</gray> <dark_gray>›</dark_gray> " + next.get().display().name());
        for (int i = 0; i < Math.min(3, rewards.size()); i++) lines.add(rewards.get(i));
        if (rewards.size() > 3) lines.add("<!italic><dark_gray>+" + (rewards.size() - 3) + " more</dark_gray>");
        lines.add("");
        lines.add("<!italic><green>Click for the full reward preview.</green>");
        return List.copyOf(lines);
    }

    private ItemStack item(String material, String name, List<String> lore, boolean glow) {
        return MenuItems.create(configs.formatter(), material, 1, "<!italic>" + name, lore, glow, 0, Map.of());
    }

    private void back(Inventory inventory) {
        inventory.setItem(45, item("ARROW", "<yellow>← Back</yellow>", List.of("<!italic><gray>Return to the rank dashboard.</gray>"), false));
        inventory.setItem(49, item("BARRIER", "<red>Close</red>", List.of(), false));
    }

    private Rank current(Player player) {
        return ranks.current(player.getUniqueId()).orElse(configs.current().registry().defaultRank());
    }

    private Optional<Rank> next(Player player, Rank current) {
        return configs.current().registry().nextAccessible(current, player::hasPermission);
    }

    private double overall(List<RequirementProgress> progress) {
        return com.zpkdxgames.plexonranks.requirement.RequirementEngine.overallProgress(progress);
    }

    private String percent(List<RequirementProgress> progress, Optional<Rank> next) {
        return NumberFormats.number((next.isEmpty() ? 1.0 : overall(progress)) * 100.0);
    }

    private long completeCount(List<RequirementProgress> progress) {
        return progress.stream().filter(RequirementProgress::complete).count();
    }

    private int completedCount(Rank current) {
        return Math.max(0, configs.current().registry().position(current));
    }

    private String journeyPercent(Rank current) {
        int maxIndex = Math.max(1, configs.current().registry().ordered().size() - 1);
        return NumberFormats.number(Math.min(1.0, Math.max(0.0,
                configs.current().registry().position(current) / (double) maxIndex)) * 100.0);
    }

    private static String statusMaterial(String status) {
        return switch (status) {
            case "COMPLETED" -> "LIME_DYE";
            case "RANK_SAVED" -> "YELLOW_DYE";
            case "INTERRUPTED", "EXTERNAL_REWARD_FAILED" -> "RED_DYE";
            default -> status.startsWith("ROLLED_BACK") ? "ORANGE_DYE" : "PAPER";
        };
    }
}
