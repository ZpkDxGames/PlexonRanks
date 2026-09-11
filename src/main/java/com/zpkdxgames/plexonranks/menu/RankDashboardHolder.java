package com.zpkdxgames.plexonranks.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

final class RankDashboardHolder implements InventoryHolder {
    enum View { ROOT, REQUIREMENTS, REWARDS, HISTORY, STATISTICS, HELP }

    private final View view;
    private final String expectedCurrentRankId;
    private final String expectedNextRankId;
    private final RankDashboardViewModel.PrimaryAction primaryAction;
    private final UUID requestId = UUID.randomUUID();
    private Inventory inventory;

    RankDashboardHolder(View view, RankDashboardViewModel model) {
        this.view = view;
        this.expectedCurrentRankId = model.current().id();
        this.expectedNextRankId = model.next().map(rank -> rank.id()).orElse("");
        this.primaryAction = model.primaryAction();
    }

    View view() {
        return view;
    }

    String expectedCurrentRankId() {
        return expectedCurrentRankId;
    }

    String expectedNextRankId() {
        return expectedNextRankId;
    }

    RankDashboardViewModel.PrimaryAction primaryAction() {
        return primaryAction;
    }

    UUID requestId() {
        return requestId;
    }

    boolean matches(RankDashboardViewModel model) {
        return expectedCurrentRankId.equals(model.current().id())
                && expectedNextRankId.equals(model.next().map(rank -> rank.id()).orElse(""));
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
