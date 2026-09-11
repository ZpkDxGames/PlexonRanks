package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.model.RankState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class RankListHolder implements InventoryHolder {
    enum View { PATH, DETAIL }

    private final View view;
    private int page;
    private int pages;
    private final int originPage;
    private final String selectedRankId;
    private final String expectedCurrentRankId;
    private final String expectedNextRankId;
    private final Map<Integer, String> ranksBySlot = new HashMap<>();
    private final Map<Integer, RankState> statesBySlot = new HashMap<>();
    private Inventory inventory;

    RankListHolder(int page, int pages) {
        this(View.PATH, page, pages, page, "", "", "");
    }

    private RankListHolder(View view, int page, int pages, int originPage, String selectedRankId,
                           String expectedCurrentRankId, String expectedNextRankId) {
        this.view = view;
        this.page = page;
        this.pages = pages;
        this.originPage = originPage;
        this.selectedRankId = selectedRankId == null ? "" : selectedRankId;
        this.expectedCurrentRankId = expectedCurrentRankId == null ? "" : expectedCurrentRankId;
        this.expectedNextRankId = expectedNextRankId == null ? "" : expectedNextRankId;
    }

    static RankListHolder detail(int originPage, String rankId, String currentRankId, String nextRankId) {
        return new RankListHolder(View.DETAIL, originPage, originPage, originPage, rankId, currentRankId, nextRankId);
    }

    View view() {
        return view;
    }

    int originPage() {
        return originPage;
    }

    String selectedRankId() {
        return selectedRankId;
    }

    boolean matches(String currentRankId, String nextRankId) {
        return expectedCurrentRankId.equals(currentRankId == null ? "" : currentRankId)
                && expectedNextRankId.equals(nextRankId == null ? "" : nextRankId);
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

    void pagination(int page, int pages) {
        this.page = page;
        this.pages = pages;
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
        if (inventory == null) throw new IllegalStateException("Inventory has not been attached");
        return inventory;
    }
}
