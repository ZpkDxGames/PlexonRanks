package com.zpkdxgames.plexonranks.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class AdminHolder implements InventoryHolder {
    enum View { LIST, EDIT, LORE, CONFIRM_DELETE }

    private final View view;
    private final int page;
    private final int pages;
    private final String rankId;
    private final Map<Integer, String> rankSlots = new HashMap<>();
    private Inventory inventory;

    AdminHolder(View view, int page, int pages, String rankId) {
        this.view = view;
        this.page = page;
        this.pages = pages;
        this.rankId = rankId;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    void rank(int slot, String id) {
        rankSlots.put(slot, id);
    }

    String rankAt(int slot) {
        return rankSlots.get(slot);
    }

    View view() { return view; }
    int page() { return page; }
    int pages() { return pages; }
    String rankId() { return rankId; }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory has not been attached");
        return inventory;
    }
}

