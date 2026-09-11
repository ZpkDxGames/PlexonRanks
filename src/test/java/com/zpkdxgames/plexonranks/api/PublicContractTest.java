package com.zpkdxgames.plexonranks.api;

import com.zpkdxgames.plexonranks.event.PlexonRankupEvent;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankHistoryEntry;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicContractTest {
    @Test
    void plexonRanksApiKeepsQuestsFacingMethods() throws Exception {
        assertReturnType(PlexonRanksAPI.class.getMethod("getRank", UUID.class), Optional.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("getNextRank", UUID.class), Optional.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("getRankById", String.class), Optional.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("getRanks"), List.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("canRankup", UUID.class), boolean.class);
    }

    @Test
    void phase2ApiAddsImmutableProgressionAndHistoryViews() throws Exception {
        assertReturnType(PlexonRanksAPI.class.getMethod("currentView", UUID.class), Optional.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("nextView", UUID.class), Optional.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("getRankViews"), List.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("progression", UUID.class), Optional.class);
        assertReturnType(PlexonRanksAPI.class.getMethod("history", UUID.class, int.class), CompletableFuture.class);

        RequirementView requirement = new RequirementView("MONEY", 200, 100, 2.0, true, Map.of("source", "vault"));
        assertEquals(1.0, requirement.progress());
        assertThrows(UnsupportedOperationException.class, () -> requirement.values().put("x", "y"));

        ProgressionView progression = new ProgressionView(UUID.randomUUID(),
                new RankView("root", 0, "Starter", "Root", "Root", "Root", false),
                Optional.empty(), List.of(requirement), 5.0, false, true);
        assertEquals(1.0, progression.progress());
        assertThrows(UnsupportedOperationException.class, () -> progression.requirements().add(requirement));
    }

    @Test
    void historyModelCarriesTransactionOutcomeWithoutMutableState() {
        RankHistoryEntry entry = new RankHistoryEntry(1, UUID.randomUUID(), "a", "b", "RANKUP", "tx", "COMPLETED",
                java.time.Instant.EPOCH, "ok");
        assertEquals("COMPLETED", entry.status());
        assertEquals("tx", entry.transactionId());
    }

    @Test
    void rankupEventKeepsDurableTransactionContract() throws Exception {
        assertReturnType(PlexonRankupEvent.class.getMethod("getPlayer"), Player.class);
        assertReturnType(PlexonRankupEvent.class.getMethod("from"), Rank.class);
        assertReturnType(PlexonRankupEvent.class.getMethod("to"), Rank.class);
        assertReturnType(PlexonRankupEvent.class.getMethod("transactionId"), String.class);
    }

    private static void assertReturnType(Method method, Class<?> expected) {
        assertEquals(expected, method.getReturnType(), method.toGenericString());
    }
}
