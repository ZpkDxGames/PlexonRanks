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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertTrue(database.commitRankup(player, "unranked", "newbie-1", UUID.randomUUID().toString()).get(5, TimeUnit.SECONDS));
        assertFalse(database.commitRankup(player, "unranked", "newbie-2", UUID.randomUUID().toString()).get(5, TimeUnit.SECONDS));
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
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("concurrent.db"));
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
    void adminCompareAndSetRejectsSecondAdminRace() throws Exception {
        UUID player = UUID.randomUUID();
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("admin-cas.db"));
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);
        var first = database.compareAndSetRank(player, "unranked", "newbie-1", "ADMIN_SET", "admin-a")
                .get(5, TimeUnit.SECONDS);
        var second = database.compareAndSetRank(player, "unranked", "newbie-2", "ADMIN_SET", "admin-b")
                .get(5, TimeUnit.SECONDS);
        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        assertEquals("newbie-1", database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        assertEquals(1, database.history(player, 20).get(5, TimeUnit.SECONDS).size());
        database.close();
    }

    @Test
    void adminCompensationCasCannotOverwriteNewerMutation() throws Exception {
        UUID player = UUID.randomUUID();
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("admin-compensation.db"));
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);
        assertTrue(database.compareAndSetRank(player, "unranked", "newbie-1", "ADMIN_SET", "first")
                .get(5, TimeUnit.SECONDS).isPresent());
        assertTrue(database.compareAndSetRank(player, "newbie-1", "newbie-2", "ADMIN_SET", "second")
                .get(5, TimeUnit.SECONDS).isPresent());
        assertTrue(database.compareAndSetRank(player, "newbie-1", "unranked", "ADMIN_ROLLBACK", "first-rollback")
                .get(5, TimeUnit.SECONDS).isEmpty());
        assertEquals("newbie-2", database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
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
        assertTrue(database.commitRankup(player, "unranked", "newbie-1", transactionId).get(5, TimeUnit.SECONDS));
        database.markTransactionFailed(transactionId, "REWARD_FAILED").get(5, TimeUnit.SECONDS);
        database.close();
        DatabaseManager reopened = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        reopened.initialize();
        reopened.close();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.prepareStatement("SELECT status FROM pr_rank_transactions WHERE id=?")) {
            statement.setString(1, transactionId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("REWARD_FAILED", result.getString("status"));
            }
        }
    }

    @Test
    void serializedWorkerUsesBoundedQueueAndReportsMetrics() throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("bounded.db"), 8);
        database.initialize();
        assertEquals(8, database.queueCapacity());
        assertEquals(0, database.queueDepth());
        database.loadOrCreate(UUID.randomUUID(), "unranked").get(5, TimeUnit.SECONDS);
        database.loadOrCreate(UUID.randomUUID(), "unranked").get(5, TimeUnit.SECONDS);
        assertEquals(2, database.submittedCount());
        assertEquals(2, database.completedCount());
        assertEquals(0, database.failureCount());
        assertTrue(database.queueHighWater() >= 0 && database.queueHighWater() <= database.queueCapacity());
        assertEquals(0, database.queueDepth());
        database.close();
    }

    @Test
    void schemaOneMigrationCreatesBackupAndPreservesPlayerRank() throws Exception {
        Path databaseFile = temp.resolve("legacy.db");
        UUID player = UUID.randomUUID();
        createSchemaOne(databaseFile, player, "technician-2");
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        database.initialize();
        assertEquals(2, database.schemaVersion());
        assertNotNull(database.migrationBackup());
        assertTrue(Files.isRegularFile(database.migrationBackup()));
        assertTrue(Files.size(database.migrationBackup()) > 0);
        assertEquals("technician-2", database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        assertTrue(database.history(player, 20).get(5, TimeUnit.SECONDS).isEmpty());
        Path backup = database.migrationBackup();
        long backupSize = Files.size(backup);
        database.close();
        DatabaseManager reopened = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        reopened.initialize();
        assertEquals("technician-2", reopened.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        assertTrue(Files.isRegularFile(backup));
        assertEquals(backupSize, Files.size(backup));
        assertTrue(reopened.history(player, 20).get(5, TimeUnit.SECONDS).isEmpty());
        reopened.close();
    }

    @Test
    void invalidExistingMigrationBackupFailsClosed() throws Exception {
        Path databaseFile = temp.resolve("invalid-backup.db");
        createSchemaOne(databaseFile, UUID.randomUUID(), "newbie-1");
        Files.createFile(temp.resolve("invalid-backup.db.pre-3.0.bak"));
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), databaseFile);
        boolean failed = false;
        try {
            database.initialize();
        } catch (IllegalStateException expected) {
            failed = true;
        } finally {
            database.close();
        }
        assertTrue(failed);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT MAX(version) FROM pr_schema")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    @Test
    void completedRankupProducesQueryableHistory() throws Exception {
        UUID player = UUID.randomUUID();
        String transaction = UUID.randomUUID().toString();
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("history.db"));
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);
        assertTrue(database.commitRankup(player, "unranked", "newbie-1", transaction).get(5, TimeUnit.SECONDS));
        database.completeTransaction(transaction).get(5, TimeUnit.SECONDS);
        var history = database.history(player, 20).get(5, TimeUnit.SECONDS);
        assertEquals(1, history.size());
        assertEquals("unranked", history.getFirst().fromRank());
        assertEquals("newbie-1", history.getFirst().toRank());
        assertEquals("RANKUP", history.getFirst().cause());
        assertEquals(transaction, history.getFirst().transactionId());
        assertEquals("COMPLETED", history.getFirst().status());
        database.close();
    }

    @Test
    void compensatingRollbackUsesCompareAndSetAndRestoresPreviousRank() throws Exception {
        UUID player = UUID.randomUUID();
        String transaction = UUID.randomUUID().toString();
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("rollback.db"));
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);
        assertTrue(database.commitRankup(player, "unranked", "newbie-1", transaction).get(5, TimeUnit.SECONDS));
        assertTrue(database.rollbackRankup(player, "newbie-1", "unranked", transaction,
                "ROLLED_BACK_REWARD_FAILURE", "test").get(5, TimeUnit.SECONDS));
        assertEquals("unranked", database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        assertEquals("ROLLED_BACK_REWARD_FAILURE", database.history(player, 1).get(5, TimeUnit.SECONDS).getFirst().status());
        database.close();
    }

    @Test
    void staleCompensationCannotOverwriteNewerAuthoritativeState() throws Exception {
        UUID player = UUID.randomUUID();
        String transaction = UUID.randomUUID().toString();
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("stale-rollback.db"));
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);
        assertTrue(database.commitRankup(player, "unranked", "newbie-1", transaction).get(5, TimeUnit.SECONDS));
        database.forceSetRank(player, "newbie-2", "TEST", UUID.randomUUID().toString()).get(5, TimeUnit.SECONDS);
        assertFalse(database.rollbackRankup(player, "newbie-1", "unranked", transaction,
                "ROLLED_BACK", "stale").get(5, TimeUnit.SECONDS));
        assertEquals("newbie-2", database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS).rankId());
        database.close();
    }

    @Test
    void adminMutationWritesBoundedHistoryRecord() throws Exception {
        UUID player = UUID.randomUUID();
        DatabaseManager database = new DatabaseManager(Logger.getAnonymousLogger(), temp.resolve("admin-history.db"));
        database.initialize();
        database.loadOrCreate(player, "unranked").get(5, TimeUnit.SECONDS);
        database.forceSetRank(player, "newbie-3", "ADMIN_SET", "admin-tx").get(5, TimeUnit.SECONDS);
        var history = database.history(player, 1).get(5, TimeUnit.SECONDS);
        assertEquals(1, history.size());
        assertEquals("ADMIN_SET", history.getFirst().cause());
        assertEquals("COMPLETED", history.getFirst().status());
        database.close();
    }

    private static void createSchemaOne(Path path, UUID player, String rank) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE pr_schema (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO pr_schema(version) VALUES(1)");
            statement.execute("CREATE TABLE pr_players (uuid TEXT PRIMARY KEY, rank_id TEXT NOT NULL, updated_at INTEGER NOT NULL, first_joined_at INTEGER NOT NULL)");
            statement.execute("CREATE TABLE pr_rank_transactions (id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, from_rank TEXT, to_rank TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, completed_at INTEGER)");
            statement.execute("CREATE INDEX pr_transactions_player_idx ON pr_rank_transactions(player_uuid, created_at)");
            try (var insert = connection.prepareStatement("INSERT INTO pr_players(uuid, rank_id, updated_at, first_joined_at) VALUES(?,?,?,?)")) {
                long now = System.currentTimeMillis();
                insert.setString(1, player.toString());
                insert.setString(2, rank);
                insert.setLong(3, now);
                insert.setLong(4, now);
                insert.executeUpdate();
            }
        }
    }
}
