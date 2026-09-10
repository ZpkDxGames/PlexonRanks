package com.zpkdxgames.plexonranks.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class Phase2ProgressionGraphTest {
    @Test
    void orderedFallbackProducesSingleReachablePath() {
        RankRegistry registry = new RankRegistry(List.of(
                rank("root", 0, true, "Starter", ""),
                rank("middle", 1, false, "Starter", ""),
                rank("max", 2, false, "Mastery", "")));
        assertEquals(List.of("root", "middle", "max"), registry.pathFromRoot().stream().map(Rank::id).toList());
        assertEquals("max", registry.terminalRank().id());
    }

    @Test
    void explicitNextEdgeIsResolved() {
        RankRegistry registry = new RankRegistry(List.of(
                rank("root", 0, true, "A", "second"),
                rank("second", 10, false, "B", "third"),
                rank("third", 20, false, "C", "")));
        assertEquals("second", registry.next(registry.defaultRank()).orElseThrow().id());
    }

    @Test
    void missingExplicitNextRankIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("root", 0, true, "A", "missing"),
                rank("max", 1, false, "B", ""))));
        assertTrue(error.getMessage().contains("missing/disabled"));
    }

    @Test
    void duplicateRankIdsAreRejectedCaseInsensitively() {
        assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("root", 0, true, "A", ""),
                rank("ROOT", 1, false, "B", ""))));
    }

    @Test
    void duplicateEnabledOrdersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("root", 0, true, "A", ""),
                rank("other", 0, false, "B", ""))));
    }

    @Test
    void backwardOrCyclicEdgeIsRejectedBeforeActivation() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("root", 0, true, "A", "second"),
                rank("second", 1, false, "B", "root"))));
        assertTrue(error.getMessage().contains("strictly higher order"));
    }

    @Test
    void unreachableRankIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("root", 0, true, "A", "third"),
                rank("second", 1, false, "B", "third"),
                rank("third", 2, false, "C", ""))));
        assertTrue(error.getMessage().contains("unreachable"));
    }

    @Test
    void defaultRootMustBeLowestEnabledOrder() {
        assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("first", 0, false, "A", ""),
                rank("root", 1, true, "B", ""))));
    }

    @Test
    void exactlyOneDefaultIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("a", 0, false, "A", ""),
                rank("b", 1, false, "B", ""))));
        assertThrows(IllegalArgumentException.class, () -> new RankRegistry(List.of(
                rank("a", 0, true, "A", ""),
                rank("b", 1, true, "B", ""))));
    }

    @Test
    void nextAccessibleCanSkipPermissionGatedNodeWithoutChangingGraph() {
        Rank gated = new Rank("gated", 1, "Premium", "max", true, true, false,
                "plexonranks.special", display("gated"), List.of(), List.of(), true, menu());
        RankRegistry registry = new RankRegistry(List.of(
                rank("root", 0, true, "Starter", "gated"),
                gated,
                rank("max", 2, false, "Mastery", "")));
        assertEquals("gated", registry.next(registry.defaultRank()).orElseThrow().id());
        assertEquals("max", registry.nextAccessible(registry.defaultRank(), permission -> false).orElseThrow().id());
    }

    @Test
    void explicitTierMetadataIsPreserved() {
        assertEquals("Newbie", rank("root", 0, true, "Newbie", "").tier());
    }

    @Test
    void omittedTierIsDeterministicallyInferredFromStableId() {
        Rank inferred = new Rank("technician-4", 4, true, true, true, "",
                display("technician-4"), List.of(), List.of(), true, menu());
        assertEquals("Technician", inferred.tier());
        Rank starter = new Rank("unranked", 0, true, true, true, "",
                display("unranked"), List.of(), List.of(), true, menu());
        assertEquals("Starter", starter.tier());
    }

    private static Rank rank(String id, int order, boolean root, String tier, String next) {
        return new Rank(id, order, tier, next, true, true, root, "", display(id), List.of(), List.of(), true, menu());
    }

    private static RankDisplay display(String id) {
        return new RankDisplay(id, id, id, List.of());
    }

    private static RankMenu menu() {
        return new RankMenu(true, "", 1, 0, false, "%rank_name%", List.of());
    }
}
