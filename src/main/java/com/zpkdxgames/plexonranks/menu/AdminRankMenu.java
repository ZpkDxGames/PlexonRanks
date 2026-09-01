package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.config.RankConfigEditor;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.service.MessageService;
import com.zpkdxgames.plexonranks.service.RenderService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdminRankMenu implements Listener {
    private static final List<Integer> RANK_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
    private static final List<String> MATERIALS = List.of("PAPER", "BOOK", "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "EMERALD", "NETHER_STAR");
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankConfigEditor editor;
    private final ChatInputManager input;
    private final MessageService messages;
    private final RenderService render;

    public AdminRankMenu(JavaPlugin plugin, ConfigManager configs, RankConfigEditor editor,
                         ChatInputManager input, MessageService messages, RenderService render) {
        this.plugin = plugin;
        this.configs = configs;
        this.editor = editor;
        this.input = input;
        this.messages = messages;
        this.render = render;
    }

    public void openList(Player player, int requestedPage) {
        List<Rank> all = configs.current().registry().all().stream().sorted(java.util.Comparator.comparingInt(Rank::order)).toList();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) RANK_SLOTS.size()));
        int page = Math.max(1, Math.min(pages, requestedPage));
        Map<String, String> values = Map.of("page", String.valueOf(page), "pages", String.valueOf(pages));
        AdminHolder holder = new AdminHolder(AdminHolder.View.LIST, page, pages, "");
        Inventory inventory = Bukkit.createInventory(holder, 54, configs.formatter().component(
                configs.current().menus().getString("admin.list-title", "Rank Editor"), values));
        holder.attach(inventory);
        fill(inventory);
        int offset = (page - 1) * RANK_SLOTS.size();
        for (int i = 0; i < RANK_SLOTS.size() && offset + i < all.size(); i++) {
            Rank rank = all.get(offset + i);
            int slot = RANK_SLOTS.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>ID:</gray> <white>" + rank.id() + "</white>");
            lore.add("<gray>Order:</gray> <white>" + rank.order() + "</white>");
            lore.add("<gray>Enabled:</gray> " + (rank.enabled() ? "<green>Yes</green>" : "<red>No</red>"));
            lore.add("");
            lore.addAll(configs.current().messages().getStringList("menu.editor-hint"));
            lore.add("<gray>Right-click:</gray> <white>Duplicate</white>");
            inventory.setItem(slot, MenuItems.create(configs.formatter(), rank.enabled() ? "WRITABLE_BOOK" : "BARRIER", 1,
                    rank.display().name(), lore, rank.menu().glow(), rank.menu().customModelData(), Map.of()));
            holder.rank(slot, rank.id());
        }
        if (page > 1) inventory.setItem(45, button("ARROW", "<yellow>Previous Page</yellow>", List.of()));
        inventory.setItem(48, button("LIME_DYE", "<green>Create Rank</green>", List.of("<gray>Enter a stable rank ID in chat.</gray>")));
        inventory.setItem(49, button("BARRIER", "<red>Close</red>", List.of()));
        if (page < pages) inventory.setItem(53, button("ARROW", "<yellow>Next Page</yellow>", List.of()));
        player.openInventory(inventory);
    }

    public void openEditor(Player player, String rankId) {
        Rank rank = configs.current().registry().byId(rankId).orElse(null);
        if (rank == null) {
            openList(player, 1);
            return;
        }
        AdminHolder holder = new AdminHolder(AdminHolder.View.EDIT, 1, 1, rankId);
        Inventory inventory = Bukkit.createInventory(holder, 54, configs.formatter().component(
                configs.current().menus().getString("admin.edit-title", "Edit %rank_short_name%"),
                Map.of("rank_short_name", rank.display().shortName())));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(10, toggle("LEVER", "<yellow>Enabled</yellow>", rank.enabled()));
        inventory.setItem(11, toggle("ENDER_EYE", "<yellow>Visible in /ranks</yellow>", rank.visible()));
        inventory.setItem(12, toggle("BELL", "<yellow>Broadcast Rank-Up</yellow>", rank.announce()));
        inventory.setItem(13, toggle("GLOW_INK_SAC", "<yellow>Menu Glow</yellow>", rank.menu().glow()));
        inventory.setItem(14, button(rank.menu().material().isBlank() ? "PAPER" : rank.menu().material(),
                "<yellow>Menu Material</yellow>", List.of("<gray>Current:</gray> <white>" + (rank.menu().material().isBlank() ? "State default" : rank.menu().material()) + "</white>",
                        "", "<gray>Left/right-click to cycle.</gray>")));
        inventory.setItem(19, button("NAME_TAG", "<yellow>Display Name</yellow>", List.of(rank.display().name(), "", "<gray>Click to edit in chat.</gray>")));
        inventory.setItem(20, button("OAK_SIGN", "<yellow>Short Name</yellow>", List.of("<white>" + rank.display().shortName() + "</white>", "", "<gray>Click to edit in chat.</gray>")));
        inventory.setItem(21, button("PAPER", "<yellow>Tag</yellow>", List.of(rank.display().tag(), "", "<gray>Click to edit in chat.</gray>")));
        inventory.setItem(22, button("COMPARATOR", "<yellow>Order: " + rank.order() + "</yellow>",
                List.of("<gray>Left-click:</gray> <white>Move earlier</white>", "<gray>Right-click:</gray> <white>Move later</white>")));
        inventory.setItem(23, button("BOOK", "<yellow>Menu Lore</yellow>", List.of("<gray>Edit, add, remove, and reorder lines.</gray>")));
        var previewProgress = render.progress(player, rank);
        Map<String, String> previewValues = render.placeholders(player, rank, rank, previewProgress, RankState.NEXT);
        List<String> previewLore = render.expand(effectiveLore(rank), rank, RankState.NEXT,
                render.requirementLines(previewProgress), render.rewardLines(rank));
        inventory.setItem(31, MenuItems.create(configs.formatter(), rank.menu().material().isBlank() ? "NETHER_STAR" : rank.menu().material(), 1,
                rank.display().name(), previewLore, rank.menu().glow(), rank.menu().customModelData(), previewValues));
        inventory.setItem(45, button("ARROW", "<yellow>Back</yellow>", List.of()));
        inventory.setItem(49, button("CHEST", "<green>Saved Live</green>", List.of("<gray>Every accepted edit is validated and reloaded atomically.</gray>")));
        inventory.setItem(53, button("BARRIER", "<red>Delete Rank</red>", List.of("<red>Requires confirmation.</red>")));
        player.openInventory(inventory);
    }

    public void openLore(Player player, String rankId) {
        Rank rank = configs.current().registry().byId(rankId).orElse(null);
        if (rank == null) return;
        List<String> lore = effectiveLore(rank);
        AdminHolder holder = new AdminHolder(AdminHolder.View.LORE, 1, 1, rankId);
        Inventory inventory = Bukkit.createInventory(holder, 54, configs.formatter().component(
                configs.current().menus().getString("admin.lore-title", "Lore • %rank_short_name%"),
                Map.of("rank_short_name", rank.display().shortName())));
        holder.attach(inventory);
        fill(inventory);
        for (int i = 0; i < Math.min(36, lore.size()); i++) {
            String line = lore.get(i);
            inventory.setItem(i, button(line.isBlank() ? "GRAY_DYE" : "PAPER", "<yellow>Line " + (i + 1) + "</yellow>",
                    List.of(line.isBlank() ? "<dark_gray>(blank line)</dark_gray>" : line, "",
                            "<gray>Left:</gray> <white>Edit</white>", "<gray>Shift-left:</gray> <white>Remove</white>",
                            "<gray>Right:</gray> <white>Move down</white>", "<gray>Shift-right:</gray> <white>Move up</white>")));
        }
        inventory.setItem(45, button("LIME_DYE", "<green>Add Line</green>", List.of()));
        inventory.setItem(46, button("GRAY_DYE", "<gray>Insert Blank Line</gray>", List.of()));
        inventory.setItem(49, button("SPONGE", "<yellow>Reset to Global Template</yellow>", List.of()));
        inventory.setItem(53, button("ARROW", "<yellow>Back</yellow>", List.of()));
        player.openInventory(inventory);
    }

    private void openDeleteConfirmation(Player player, String rankId) {
        Rank rank = configs.current().registry().byId(rankId).orElse(null);
        if (rank == null) return;
        AdminHolder holder = new AdminHolder(AdminHolder.View.CONFIRM_DELETE, 1, 1, rankId);
        Inventory inventory = Bukkit.createInventory(holder, 27, configs.formatter().component("<red><bold>Delete " + rank.display().shortName() + "?</bold></red>"));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(11, button("LIME_CONCRETE", "<green>Cancel</green>", List.of()));
        inventory.setItem(15, button("RED_CONCRETE", "<red>Delete Permanently</red>",
                List.of("<gray>The YAML entry will be removed.</gray>", "<gray>Affected database rows use the configured join fallback.</gray>")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        switch (holder.view()) {
            case LIST -> clickList(player, holder, slot, event.getClick());
            case EDIT -> clickEditor(player, holder.rankId(), slot, event.getClick());
            case LORE -> clickLore(player, holder.rankId(), slot, event.getClick());
            case CONFIRM_DELETE -> clickConfirmation(player, holder.rankId(), slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminHolder) event.setCancelled(true);
    }

    private void clickList(Player player, AdminHolder holder, int slot, ClickType click) {
        String rankId = holder.rankAt(slot);
        if (rankId != null) {
            if (click == ClickType.SHIFT_LEFT) {
                Rank rank = configs.current().registry().byId(rankId).orElseThrow();
                finish(player, editor.edit(rankId, section -> section.set("enabled", !rank.enabled())), () -> openList(player, holder.page()));
            } else if (click.isRightClick()) {
                input.request(player, value -> finish(player, editor.duplicate(rankId, value), () -> openList(player, holder.page())),
                        () -> openList(player, holder.page()));
            } else {
                openEditor(player, rankId);
            }
            return;
        }
        if (slot == 45 && holder.page() > 1) openList(player, holder.page() - 1);
        else if (slot == 53 && holder.page() < holder.pages()) openList(player, holder.page() + 1);
        else if (slot == 48) input.request(player, value -> finish(player, editor.create(value), () -> openList(player, holder.page())),
                    () -> openList(player, holder.page()));
        else if (slot == 49) player.closeInventory();
    }

    private void clickEditor(Player player, String rankId, int slot, ClickType click) {
        Rank rank = configs.current().registry().byId(rankId).orElse(null);
        if (rank == null) return;
        if (slot == 10) finish(player, editor.edit(rankId, section -> section.set("enabled", !rank.enabled())), () -> openEditor(player, rankId));
        else if (slot == 11) finish(player, editor.edit(rankId, section -> section.set("visible", !rank.visible())), () -> openEditor(player, rankId));
        else if (slot == 12) finish(player, editor.edit(rankId, section -> section.set("announce", !rank.announce())), () -> openEditor(player, rankId));
        else if (slot == 13) finish(player, editor.edit(rankId, section -> section.set("menu.glow", !rank.menu().glow())), () -> openEditor(player, rankId));
        else if (slot == 14) {
            String current = rank.menu().material().isBlank() ? "PAPER" : rank.menu().material();
            int index = Math.max(0, MATERIALS.indexOf(current.toUpperCase()));
            int next = Math.floorMod(index + (click.isRightClick() ? -1 : 1), MATERIALS.size());
            finish(player, editor.edit(rankId, section -> section.set("menu.material", MATERIALS.get(next))), () -> openEditor(player, rankId));
        } else if (slot == 19) prompt(player, rankId, "display.name", rank.display().name());
        else if (slot == 20) prompt(player, rankId, "display.short-name", rank.display().shortName());
        else if (slot == 21) prompt(player, rankId, "display.tag", rank.display().tag());
        else if (slot == 22) finish(player, editor.swapOrder(rankId, click.isRightClick() ? 1 : -1), () -> openEditor(player, rankId));
        else if (slot == 23) openLore(player, rankId);
        else if (slot == 45) openList(player, Math.max(1, configs.current().registry().position(rank) / RANK_SLOTS.size() + 1));
        else if (slot == 53) openDeleteConfirmation(player, rankId);
    }

    private void clickLore(Player player, String rankId, int slot, ClickType click) {
        Rank rank = configs.current().registry().byId(rankId).orElse(null);
        if (rank == null) return;
        List<String> lore = new ArrayList<>(effectiveLore(rank));
        if (slot < Math.min(36, lore.size())) {
            if (click == ClickType.SHIFT_LEFT) {
                lore.remove(slot);
                saveLore(player, rankId, lore);
            } else if (click == ClickType.SHIFT_RIGHT) {
                if (slot > 0) java.util.Collections.swap(lore, slot, slot - 1);
                saveLore(player, rankId, lore);
            } else if (click.isRightClick()) {
                if (slot + 1 < lore.size()) java.util.Collections.swap(lore, slot, slot + 1);
                saveLore(player, rankId, lore);
            } else {
                int index = slot;
                input.request(player, value -> {
                    List<String> updated = new ArrayList<>(effectiveLore(configs.current().registry().byId(rankId).orElseThrow()));
                    if (index < updated.size()) updated.set(index, value);
                    saveLore(player, rankId, updated);
                }, () -> openLore(player, rankId));
            }
        } else if (slot == 45) {
            input.request(player, value -> {
                List<String> updated = new ArrayList<>(effectiveLore(configs.current().registry().byId(rankId).orElseThrow()));
                updated.add(value);
                saveLore(player, rankId, updated);
            }, () -> openLore(player, rankId));
        } else if (slot == 46) {
            lore.add("");
            saveLore(player, rankId, lore);
        } else if (slot == 49) {
            finish(player, editor.edit(rankId, section -> {
                section.set("menu.use-global-template", true);
                section.set("menu.lore", null);
            }), () -> openLore(player, rankId));
        } else if (slot == 53) openEditor(player, rankId);
    }

    private void clickConfirmation(Player player, String rankId, int slot) {
        if (slot == 11) openEditor(player, rankId);
        else if (slot == 15) finish(player, editor.delete(rankId), () -> openList(player, 1));
    }

    private void prompt(Player player, String rankId, String path, String previous) {
        input.request(player, value -> finish(player, editor.edit(rankId, section -> section.set(path, value)), () -> openEditor(player, rankId)),
                () -> openEditor(player, rankId));
    }

    private void saveLore(Player player, String rankId, List<String> lore) {
        finish(player, editor.edit(rankId, section -> {
            section.set("menu.use-global-template", false);
            section.set("menu.lore", lore);
        }), () -> openLore(player, rankId));
    }

    private void finish(Player player, RankConfigEditor.EditResult result, Runnable reopen) {
        if (result.success()) {
            messages.send(player, "admin.editor-saved");
        } else {
            messages.send(player, "admin.editor-save-failed");
            result.details().stream().limit(3).forEach(detail -> player.sendMessage(configs.formatter().component("<red>• " + detail + "</red>")));
        }
        reopen.run();
    }

    private List<String> effectiveLore(Rank rank) {
        if (rank.menu().useGlobalTemplate() || rank.menu().lore().isEmpty()) {
            return new ArrayList<>(configs.current().menus().getStringList("rank-list.rank-template.lore"));
        }
        return new ArrayList<>(rank.menu().lore());
    }

    private org.bukkit.inventory.ItemStack toggle(String material, String name, boolean enabled) {
        return button(material, name, List.of("<gray>Current:</gray> " + (enabled ? "<green>Enabled</green>" : "<red>Disabled</red>"), "", "<gray>Click to toggle.</gray>"));
    }

    private org.bukkit.inventory.ItemStack button(String material, String name, List<String> lore) {
        return MenuItems.create(configs.formatter(), material, 1, name, lore, false, 0, Map.of());
    }

    private void fill(Inventory inventory) {
        var filler = button("BLACK_STAINED_GLASS_PANE", " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }
}
