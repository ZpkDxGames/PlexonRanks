package com.zpkdxgames.plexonranks.database;

import com.zpkdxgames.plexonranks.model.PlayerRankData;
import com.zpkdxgames.plexonranks.model.RankHistoryEntry;
import org.sqlite.JDBC;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class DatabaseManager implements AutoCloseable {
    private static final int SCHEMA_VERSION = 2;
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private final Logger logger;
    private final Path databasePath;
    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final AtomicInteger queueHighWater = new AtomicInteger();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private volatile String lastFailure = "NONE";
    private volatile Path migrationBackup;
    private Connection connection;

    public DatabaseManager(org.bukkit.plugin.java.JavaPlugin plugin, String configuredFile) {
        this(plugin, configuredFile, DEFAULT_QUEUE_CAPACITY);
    }

    public DatabaseManager(org.bukkit.plugin.java.JavaPlugin plugin, String configuredFile, int queueCapacity) {
        this.logger = plugin.getLogger();
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path resolved = dataFolder.resolve(configuredFile).normalize();
        if (!resolved.startsWith(dataFolder)) {
            throw new IllegalArgumentException("SQLite file must remain inside the PlexonRanks data folder");
        }
        this.databasePath = resolved;
        this.queueCapacity = Math.max(8, queueCapacity);
        this.executor = createExecutor("PlexonRanks-Database", this.queueCapacity);
    }

    public DatabaseManager(Logger logger, Path databasePath) {
        this(logger, databasePath, DEFAULT_QUEUE_CAPACITY);
    }

    public DatabaseManager(Logger logger, Path databasePath, int queueCapacity) {
        this.logger = logger;
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.queueCapacity = Math.max(8, queueCapacity);
        this.executor = createExecutor("PlexonRanks-Database-Testable", this.queueCapacity);
    }

    public void initialize() throws Exception {
        Files.createDirectories(databasePath.getParent());
        Class.forName(JDBC.class.getName());
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("CREATE TABLE IF NOT EXISTS pr_schema (version INTEGER NOT NULL)");
        }
        migrate();
        int interrupted;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pr_rank_transactions SET status='INTERRUPTED', completed_at=? WHERE status IN ('PREPARED','RANK_SAVED')")) {
            statement.setLong(1, System.currentTimeMillis());
            interrupted = statement.executeUpdate();
        }
        if (interrupted > 0) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE pr_rank_history SET status='INTERRUPTED', detail='Interrupted by restart before transaction completion' "
                            + "WHERE status IN ('PREPARED','RANK_SAVED')")) {
                statement.executeUpdate();
            }
            logger.warning("Marked " + interrupted + " interrupted rank transaction(s); irreversible rewards were not replayed.");
        }
    }

    public CompletableFuture<PlayerRankData> loadOrCreate(UUID uuid, String defaultRankId) {
        return submit(connection -> {
            PlayerRankData existing = loadExistingOrNull(connection, uuid);
            if (existing != null) return existing;
            long now = System.currentTimeMillis();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO pr_players(uuid, rank_id, updated_at, first_joined_at) VALUES(?,?,?,?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, defaultRankId);
                insert.setLong(3, now);
                insert.setLong(4, now);
                insert.executeUpdate();
            } catch (SQLException race) {
                if (race.getMessage() == null || !race.getMessage().toLowerCase().contains("unique")) throw race;
                return loadExisting(connection, uuid);
            }
            return new PlayerRankData(uuid, defaultRankId, Instant.ofEpochMilli(now), Instant.ofEpochMilli(now));
        });
    }

    public CompletableFuture<Boolean> commitRankup(UUID uuid, String expectedFrom, String toRank, String transactionId) {
        return submit(connection -> {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO pr_rank_transactions(id, player_uuid, from_rank, to_rank, status, created_at) VALUES(?,?,?,?,?,?)")) {
                    insert.setString(1, transactionId);
                    insert.setString(2, uuid.toString());
                    insert.setString(3, expectedFrom);
                    insert.setString(4, toRank);
                    insert.setString(5, "PREPARED");
                    insert.setLong(6, now);
                    insert.executeUpdate();
                }
                int changed = updateRankCas(connection, uuid, expectedFrom, toRank, now);
                if (changed != 1) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE pr_rank_transactions SET status='RANK_SAVED' WHERE id=?")) {
                    update.setString(1, transactionId);
                    update.executeUpdate();
                }
                insertHistory(connection, uuid, expectedFrom, toRank, "RANKUP", transactionId,
                        "RANK_SAVED", now, "Authoritative rank compare-and-set committed");
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        });
    }

    public CompletableFuture<Boolean> rollbackRankup(UUID uuid, String expectedCurrent, String restoreRank,
                                                      String transactionId, String status, String detail) {
        return submit(connection -> {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                int changed = updateRankCas(connection, uuid, expectedCurrent, restoreRank, now);
                if (changed != 1) {
                    connection.rollback();
                    return false;
                }
                updateTransactionStatus(connection, transactionId, status, detail, now);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        });
    }

    /**
     * Administrative compare-and-set mutation. Missing players and stale rank expectations fail closed.
     * The history row is written atomically with the authoritative rank change.
     */
    public CompletableFuture<Optional<PlayerRankData>> compareAndSetRank(UUID uuid, String expectedRankId,
                                                                         String rankId, String cause,
                                                                         String transactionId) {
        return submit(connection -> {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                int changed = updateRankCas(connection, uuid, expectedRankId, rankId, now);
                if (changed != 1) {
                    connection.rollback();
                    return Optional.empty();
                }
                if (!expectedRankId.equalsIgnoreCase(rankId)) {
                    insertHistory(connection, uuid, expectedRankId, rankId, cause, transactionId,
                            "COMPLETED", now, "Administrative authoritative rank compare-and-set");
                }
                PlayerRankData data = loadExisting(connection, uuid);
                connection.commit();
                return Optional.of(data);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        });
    }

    public CompletableFuture<Void> completeTransaction(String transactionId) {
        return updateTransaction(transactionId, "COMPLETED", "All reversible and external reward stages completed");
    }

    public CompletableFuture<Void> markTransactionFailed(String transactionId, String status) {
        return markTransactionFailed(transactionId, status, "");
    }

    public CompletableFuture<Void> markTransactionFailed(String transactionId, String status, String detail) {
        return updateTransaction(transactionId, status, detail);
    }

    private CompletableFuture<Void> updateTransaction(String transactionId, String status, String detail) {
        return submit(connection -> {
            updateTransactionStatus(connection, transactionId, status, detail, System.currentTimeMillis());
            return null;
        });
    }

    public CompletableFuture<PlayerRankData> forceSetRank(UUID uuid, String rankId) {
        return forceSetRank(uuid, rankId, "ADMIN_SET", UUID.randomUUID().toString());
    }

    /** Used only for explicit repair/bootstrap paths where no prior rank CAS contract exists. */
    public CompletableFuture<PlayerRankData> forceSetRank(UUID uuid, String rankId, String cause, String transactionId) {
        return submit(connection -> {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                PlayerRankData before = loadExistingOrNull(connection, uuid);
                String from = before == null ? "" : before.rankId();
                if (before == null) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO pr_players(uuid, rank_id, updated_at, first_joined_at) VALUES(?,?,?,?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setString(2, rankId);
                        insert.setLong(3, now);
                        insert.setLong(4, now);
                        insert.executeUpdate();
                    }
                } else {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE pr_players SET rank_id=?, updated_at=? WHERE uuid=?")) {
                        update.setString(1, rankId);
                        update.setLong(2, now);
                        update.setString(3, uuid.toString());
                        update.executeUpdate();
                    }
                }
                if (!from.equalsIgnoreCase(rankId)) {
                    insertHistory(connection, uuid, from, rankId, cause, transactionId,
                            "COMPLETED", now, "Authoritative rank repair/bootstrap mutation");
                }
                connection.commit();
                return loadExisting(connection, uuid);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        });
    }

    public CompletableFuture<List<RankHistoryEntry>> history(UUID uuid, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        return submit(connection -> {
            List<RankHistoryEntry> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, player_uuid, from_rank, to_rank, cause, transaction_id, status, created_at, detail "
                            + "FROM pr_rank_history WHERE player_uuid=? ORDER BY id DESC LIMIT ?")) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new RankHistoryEntry(
                                rows.getLong("id"), UUID.fromString(rows.getString("player_uuid")),
                                rows.getString("from_rank"), rows.getString("to_rank"), rows.getString("cause"),
                                rows.getString("transaction_id"), rows.getString("status"),
                                Instant.ofEpochMilli(rows.getLong("created_at")), rows.getString("detail")));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Path> backupTo(Path target) {
        return submit(connection -> {
            Path normalized = target.toAbsolutePath().normalize();
            Files.createDirectories(normalized.getParent());
            if (Files.exists(normalized)) throw new IllegalStateException("Backup target already exists: " + normalized.getFileName());
            try (Statement checkpoint = connection.createStatement()) {
                checkpoint.execute("PRAGMA wal_checkpoint(FULL)");
                checkpoint.execute("VACUUM INTO '" + normalized.toString().replace("'", "''") + "'");
            }
            return normalized;
        });
    }

    public Path databasePath() { return databasePath; }
    public int schemaVersion() { return SCHEMA_VERSION; }
    public Path migrationBackup() { return migrationBackup; }
    public String lastFailure() { return lastFailure; }
    public int queueDepth() { return executor.getQueue().size(); }
    public int queueCapacity() { return queueCapacity; }
    public int queueHighWater() { return queueHighWater.get(); }
    public long submittedCount() { return submitted.get(); }
    public long completedCount() { return completed.get(); }
    public long failureCount() { return failures.get(); }

    private void migrate() throws Exception {
        int current = 0;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT MAX(version) FROM pr_schema")) {
            if (result.next()) current = result.getInt(1);
        }
        if (current > SCHEMA_VERSION) {
            throw new SQLException("Database schema " + current + " is newer than supported schema " + SCHEMA_VERSION);
        }
        if (current < 1) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE pr_players (uuid TEXT PRIMARY KEY, rank_id TEXT NOT NULL, updated_at INTEGER NOT NULL, first_joined_at INTEGER NOT NULL)");
                statement.execute("CREATE TABLE pr_rank_transactions (id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, from_rank TEXT, to_rank TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, completed_at INTEGER)");
                statement.execute("CREATE INDEX pr_transactions_player_idx ON pr_rank_transactions(player_uuid, created_at)");
                statement.execute("DELETE FROM pr_schema");
                statement.execute("INSERT INTO pr_schema(version) VALUES(1)");
                connection.commit();
                current = 1;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        }
        if (current < 2) {
            createPre3Backup();
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS pr_rank_history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "player_uuid TEXT NOT NULL,"
                        + "from_rank TEXT,"
                        + "to_rank TEXT NOT NULL,"
                        + "cause TEXT NOT NULL,"
                        + "transaction_id TEXT,"
                        + "status TEXT NOT NULL,"
                        + "created_at INTEGER NOT NULL,"
                        + "detail TEXT NOT NULL DEFAULT '')");
                statement.execute("CREATE INDEX IF NOT EXISTS pr_history_player_idx ON pr_rank_history(player_uuid, id DESC)");
                statement.execute("DELETE FROM pr_schema");
                statement.execute("INSERT INTO pr_schema(version) VALUES(2)");
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        }
    }

    private void createPre3Backup() throws Exception {
        Path backup = databasePath.resolveSibling(databasePath.getFileName() + ".pre-3.0.bak");
        if (Files.exists(backup)) {
            if (!Files.isRegularFile(backup) || Files.size(backup) == 0L) {
                throw new IllegalStateException("Existing pre-3.0 migration backup is empty or invalid: " + backup.getFileName());
            }
            migrationBackup = backup;
            return;
        }
        try (Statement checkpoint = connection.createStatement()) {
            checkpoint.execute("PRAGMA wal_checkpoint(FULL)");
            checkpoint.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
        }
        if (!Files.isRegularFile(backup) || Files.size(backup) == 0L) {
            throw new IllegalStateException("Pre-3.0 SQLite migration backup was not created safely");
        }
        migrationBackup = backup;
        logger.info("Created pre-3.0 SQLite migration backup: " + backup.getFileName());
    }

    private static int updateRankCas(Connection connection, UUID uuid, String expectedRankId,
                                     String rankId, long now) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE pr_players SET rank_id=?, updated_at=? WHERE uuid=? AND rank_id=?")) {
            update.setString(1, rankId);
            update.setLong(2, now);
            update.setString(3, uuid.toString());
            update.setString(4, expectedRankId);
            return update.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, UUID uuid, String fromRank, String toRank, String cause,
                               String transactionId, String status, long createdAt, String detail) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO pr_rank_history(player_uuid, from_rank, to_rank, cause, transaction_id, status, created_at, detail) "
                        + "VALUES(?,?,?,?,?,?,?,?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, fromRank == null ? "" : fromRank);
            insert.setString(3, toRank);
            insert.setString(4, cause);
            insert.setString(5, transactionId);
            insert.setString(6, status);
            insert.setLong(7, createdAt);
            insert.setString(8, detail == null ? "" : detail);
            insert.executeUpdate();
        }
    }

    private void updateTransactionStatus(Connection connection, String transactionId, String status,
                                         String detail, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pr_rank_transactions SET status=?, completed_at=? WHERE id=?")) {
            statement.setString(1, status);
            statement.setLong(2, now);
            statement.setString(3, transactionId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pr_rank_history SET status=?, detail=? WHERE transaction_id=?")) {
            statement.setString(1, status);
            statement.setString(2, detail == null ? "" : detail);
            statement.setString(3, transactionId);
            statement.executeUpdate();
        }
    }

    private PlayerRankData loadExisting(Connection connection, UUID uuid) throws SQLException {
        PlayerRankData existing = loadExistingOrNull(connection, uuid);
        if (existing == null) throw new SQLException("Player row was not found after creation race");
        return existing;
    }

    private PlayerRankData loadExistingOrNull(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT rank_id, updated_at, first_joined_at FROM pr_players WHERE uuid=?")) {
            select.setString(1, uuid.toString());
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) return null;
                return new PlayerRankData(uuid, result.getString("rank_id"),
                        Instant.ofEpochMilli(result.getLong("updated_at")),
                        Instant.ofEpochMilli(result.getLong("first_joined_at")));
            }
        }
    }

    private <T> CompletableFuture<T> submit(SqlFunction<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        submitted.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    T result = task.apply(connection);
                    lastFailure = "NONE";
                    future.complete(result);
                } catch (Throwable throwable) {
                    failures.incrementAndGet();
                    lastFailure = rootMessage(throwable);
                    future.completeExceptionally(throwable);
                } finally {
                    completed.incrementAndGet();
                }
            });
            queueHighWater.accumulateAndGet(executor.getQueue().size(), Math::max);
        } catch (RejectedExecutionException exception) {
            failures.incrementAndGet();
            lastFailure = "PlexonRanks database queue is full or shutting down (depth=" + queueDepth()
                    + ", capacity=" + queueCapacity + ")";
            future.completeExceptionally(new IllegalStateException(lastFailure, exception));
        }
        return future;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                int unfinished = executor.getQueue().size();
                logger.warning("Database worker did not drain within 5s; cancelling " + unfinished + " queued operation(s).");
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warning("Database worker still active after forced shutdown; closing SQLite connection defensively.");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException exception) {
            lastFailure = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            logger.warning("Database did not close cleanly: " + lastFailure);
        }
    }

    private static ThreadPoolExecutor createExecutor(String name, int queueCapacity) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }
}
