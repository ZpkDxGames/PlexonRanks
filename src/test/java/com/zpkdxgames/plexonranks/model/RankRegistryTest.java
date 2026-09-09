package com.zpkdxgames.plexonranks.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankRegistryTest {
    @Test
    void ordersAndResolvesProgressionByStableId() {
        RankRegistry registry = new RankRegistry(List.of(rank("pro-1", 2, false, ""),
                rank("unranked", 0, true, ""), rank("newbie-1", 1, false, "")));

        assertEquals("unranked", registry.defaultRank().id());
        assertEquals(List.of("unranked", "newbie-1", "pro-1"), registry.ordered().stream().map(Rank::id).toList());
        assertEquals(0, registry.position(registry.defaultRank()));
        assertEquals(1, registry.position(registry.byId("newbie-1").orElseThrow()));
        assertEquals("newbie-1", registry.next(registry.defaultRank()).orElseThrow().id());
        assertEquals("pro-1", registry.shift(registry.defaultRank(), 20).orElseThrow().id());
        assertEquals("unranked", registry.shift(registry.defaultRank(), -20).orElseThrow().id());
    }

    @Test
    void skipsPermissionControlledRankWithoutPermission() {
        RankRegistry registry = new RankRegistry(List.of(rank("start", 0, true, ""),
                rank("special", 1, false, "rank.special"), rank("normal", 2, false, "")));

        assertEquals("normal", registry.nextAccessible(registry.defaultRank(), permission -> false).orElseThrow().id());
        assertEquals("special", registry.nextAccessible(registry.defaultRank(), permission -> true).orElseThrow().id());
    }

    @Test
    void precomputesVisibleEnabledRanksWithoutChangingOrderedProgression() {
        RankRegistry registry = new RankRegistry(List.of(
                rank("start", 0, true, true, true, ""),
                rank("hidden", 1, false, true, false, ""),
                rank("disabled", 2, false, false, true, ""),
                rank("finish", 3, false, true, true, "")
        ));

        assertEquals(List.of("start", "hidden", "finish"),
                registry.ordered().stream().map(Rank::id).toList());
        assertEquals(List.of("start", "finish"), registry.visible().stream().map(Rank::id).toList());
        assertEquals("hidden", registry.nextAccessible(registry.defaultRank(), permission -> true).orElseThrow().id());
    }

    @Test
    void rejectsDuplicateStableIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new RankRegistry(List.of(rank("same", 0, true, ""), rank("same", 1, false, ""))));
    }

    @Test
    void directLookupNeverReturnsDisabledRanks() {
        RankRegistry registry = new RankRegistry(List.of(
                rank("start", 0, true, true, ""),
                rank("disabled", 1, false, false, ""),
                rank("finish", 2, false, true, "")));

        assertTrue(registry.find("disabled").isEmpty());
        assertEquals(List.of("start", "finish"), registry.ordered().stream().map(Rank::id).toList());
    }

    private static Rank rank(String id, int order, boolean defaultRank, String bypass) {
        return rank(id, order, defaultRank, true, true, bypass);
    }

    private static Rank rank(String id, int order, boolean defaultRank, boolean enabled, String bypass) {
        return rank(id, order, defaultRank, enabled, true, bypass);
    }

    private static Rank rank(String id, int order, boolean defaultRank, boolean enabled, boolean visible, String bypass) {
        return new Rank(id, order, enabled, visible, defaultRank, bypass,
                new RankDisplay(id, id, id, List.of()), List.of(), List.of(), false,
                new RankMenu(true, "", 1, 0, false, "%rank_name%", List.of()));
    }
}
