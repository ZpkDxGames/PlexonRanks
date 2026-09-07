package com.zpkdxgames.plexonranks.api;

import com.zpkdxgames.plexonranks.event.PlexonRankupEvent;
import com.zpkdxgames.plexonranks.model.Rank;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
