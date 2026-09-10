package com.zpkdxgames.plexonranks.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class RankDashboardHolder implements InventoryHolder {
    enum View { ROOT, REQUIREMENTS, REWARDS, HISTORY, STATISTICS, HELP }

    private final View view;
    private Inventory inventory;

    RankDashboardHolder(View view) {
        this.view = view;
    }

    View view() {
        return view;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory has not been attached");
        return inventory;
    }
}
