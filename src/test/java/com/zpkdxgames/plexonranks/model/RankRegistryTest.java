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
    void rejectsDuplicateStableIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new RankRegistry(List.of(rank("same", 0, true, ""), rank("same", 1, false, ""))));
    }

    private static Rank rank(String id, int order, boolean defaultRank, String bypass) {
        return new Rank(id, order, true, true, defaultRank, bypass,
                new RankDisplay(id, id, id, List.of()), List.of(), List.of(), false,
                new RankMenu(true, "", 1, 0, false, "%rank_name%", List.of()));
    }
}

