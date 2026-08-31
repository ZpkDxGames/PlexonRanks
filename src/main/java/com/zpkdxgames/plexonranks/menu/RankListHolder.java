package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.model.RankState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class RankListHolder implements InventoryHolder {
    private final int page;
    private final int pages;
    private final Map<Integer, String> ranksBySlot = new HashMap<>();
    private final Map<Integer, RankState> statesBySlot = new HashMap<>();
    private Inventory inventory;

    RankListHolder(int page, int pages) {
        this.page = page;
        this.pages = pages;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    void rank(int slot, String rankId, RankState state) {
        ranksBySlot.put(slot, rankId);
        statesBySlot.put(slot, state);
    }

    void clearRanks() {
        ranksBySlot.clear();
        statesBySlot.clear();
    }

    String rankAt(int slot) {
        return ranksBySlot.get(slot);
    }

    RankState stateAt(int slot) {
        return statesBySlot.get(slot);
    }

    int page() {
        return page;
    }

    int pages() {
        return pages;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Inventory has not been attached");
        }
        return inventory;
    }
}
