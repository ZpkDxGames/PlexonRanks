package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {
    @TempDir
    Path temp;

    @Test
    void persistsRankAndRejectsStaleTransitions() throws Exception {
        UUID player = UUID.randomUUID();
        Path databaseFile = temp.resolve("database.db");
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        database.initialize();

        assertEquals("unranked", database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        assertTrue(database.commitRankup(player, "unranked", "newbie-1", UUID.randomUUID().toString())
                .get(5, TimeUnit.SECONDS));
        assertFalse(database.commitRankup(player, "unranked", "newbie-2", UUID.randomUUID().toString())
                .get(5, TimeUnit.SECONDS));
        Path backup = database.backupTo(temp.resolve("backup.db")).get(5, TimeUnit.SECONDS);
        assertTrue(Files.isRegularFile(backup));
        database.close();

        DatabaseManager reopened = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        reopened.initialize();
        assertEquals("newbie-1", reopened.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        reopened.close();
    }

    @Test
    void onlyOneCompetingRankTransactionCanCommit() throws Exception {
        UUID player = UUID.randomUUID();
        Path databaseFile = temp.resolve("concurrent.db");
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);

        var first = database.commitRankup(player, "unranked", "newbie-1", UUID.randomUUID().toString());
        var second = database.commitRankup(player, "unranked", "newbie-2", UUID.randomUUID().toString());

        assertTrue(first.get(5, TimeUnit.SECONDS));
        assertFalse(second.get(5, TimeUnit.SECONDS));
        assertEquals("newbie-1", database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        database.close();
    }

    @Test
    void startupDoesNotRewriteRewardFailuresAsInterrupted() throws Exception {
        UUID player = UUID.randomUUID();
        String transactionId = UUID.randomUUID().toString();
        Path databaseFile = temp.resolve("reward-failure.db");
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);
        assertTrue(database.commitRankup(player, "unranked", "newbie-1", transactionId)
                .get(5, TimeUnit.SECONDS));
        database.markTransactionFailed(transactionId, "REWARD_FAILED").get(5, TimeUnit.SECONDS);
        database.close();

        DatabaseManager reopened = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        reopened.initialize();
        reopened.close();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.prepareStatement(
                     "SELECT status FROM pr_rank_transactions WHERE id=?")) {
            statement.setString(1, transactionId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("REWARD_FAILED", result.getString("status"));
            }
        }
    }

    @Test
    void serializedWorkerUsesBoundedQueueAndReportsMetrics() throws Exception {
        Path databaseFile = temp.resolve("bounded.db");
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile, 8);
        database.initialize();

        assertEquals(8, database.queueCapacity());
        assertEquals(0, database.queueDepth());
        database.loadOrCreate(UUID.randomUUID(), "unranked").get(5, TimeUnit.SECONDS);
        database.loadOrCreate(UUID.randomUUID(), "unranked").get(5, TimeUnit.SECONDS);

        assertEquals(2, database.submittedCount());
        assertEquals(2, database.completedCount());
        assertEquals(0, database.failureCount());
        assertTrue(database.queueHighWater() >= 0);
        assertTrue(database.queueHighWater() <= database.queueCapacity());
        assertEquals(0, database.queueDepth());
        database.close();
    }
}
