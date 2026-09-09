package com.zpkdxgames.plexonranks.database;

import com.zpkdxgames.plexonranks.model.PlayerRankData;
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
    private static final int SCHEMA_VERSION = 1;
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private final Logger logger;
    private final Path databasePath;
    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final AtomicInteger queueHighWater = new AtomicInteger();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private Connection connection;

    public DatabaseManager(org.bukkit.plugin.java.JavaPlugin plugin, String configuredFile) {
        this.logger = plugin.getLogger();
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path resolved = dataFolder.resolve(configuredFile).normalize();
        if (!resolved.startsWith(dataFolder)) {
            throw new IllegalArgumentException("SQLite file must remain inside the PlexonRanks data folder");
        }
        this.databasePath = resolved;
        this.queueCapacity = DEFAULT_QUEUE_CAPACITY;
        this.executor = createExecutor("PlexonRanks-Database", queueCapacity);
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
            logger.warning("Marked " + interrupted + " interrupted rank transaction(s); one-time rewards were not replayed.");
        }
    }

    public CompletableFuture<PlayerRankData> loadOrCreate(UUID uuid, String defaultRankId) {
        return submit(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT rank_id, updated_at, first_joined_at FROM pr_players WHERE uuid=?")) {
                select.setString(1, uuid.toString());
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        return new PlayerRankData(uuid, result.getString("rank_id"),
                                Instant.ofEpochMilli(result.getLong("updated_at")),
                                Instant.ofEpochMilli(result.getLong("first_joined_at")));
                    }
                }
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO pr_players(uuid, rank_id, updated_at, first_joined_at) VALUES(?,?,?,?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, defaultRankId);
                insert.setLong(3, now);
                insert.setLong(4, now);
                insert.executeUpdate();
            } catch (SQLException race) {
                if (!race.getMessage().toLowerCase().contains("unique")) {
                    throw race;
                }
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
                int changed;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE pr_players SET rank_id=?, updated_at=? WHERE uuid=? AND rank_id=?")) {
                    update.setString(1, toRank);
                    update.setLong(2, now);
                    update.setString(3, uuid.toString());
                    update.setString(4, expectedFrom);
                    changed = update.executeUpdate();
                }
                if (changed != 1) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE pr_rank_transactions SET status='RANK_SAVED' WHERE id=?")) {
                    update.setString(1, transactionId);
                    update.executeUpdate();
                }
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

    public CompletableFuture<Void> completeTransaction(String transactionId) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE pr_rank_transactions SET status='COMPLETED', completed_at=? WHERE id=? AND status='RANK_SAVED'")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, transactionId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> markTransactionFailed(String transactionId, String status) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE pr_rank_transactions SET status=?, completed_at=? WHERE id=?")) {
                statement.setString(1, status);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, transactionId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<PlayerRankData> forceSetRank(UUID uuid, String rankId) {
        return submit(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE pr_players SET rank_id=?, updated_at=? WHERE uuid=?")) {
                update.setString(1, rankId);
                update.setLong(2, now);
                update.setString(3, uuid.toString());
                if (update.executeUpdate() == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO pr_players(uuid, rank_id, updated_at, first_joined_at) VALUES(?,?,?,?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setString(2, rankId);
                        insert.setLong(3, now);
                        insert.setLong(4, now);
                        insert.executeUpdate();
                    }
                }
            }
            return loadExisting(connection, uuid);
        });
    }

    public CompletableFuture<Path> backupTo(Path target) {
        return submit(connection -> {
            Path normalized = target.toAbsolutePath().normalize();
            Files.createDirectories(normalized.getParent());
            if (Files.exists(normalized)) {
                throw new IllegalStateException("Backup target already exists: " + normalized.getFileName());
            }
            try (Statement checkpoint = connection.createStatement()) {
                checkpoint.execute("PRAGMA wal_checkpoint(FULL)");
                checkpoint.execute("VACUUM INTO '" + normalized.toString().replace("'", "''") + "'");
            }
            return normalized;
        });
    }

    public Path databasePath() {
        return databasePath;
    }

    public int queueDepth() {
        return executor.getQueue().size();
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int queueHighWater() {
        return queueHighWater.get();
    }

    public long submittedCount() {
        return submitted.get();
    }

    public long completedCount() {
        return completed.get();
    }

    public long failureCount() {
        return failures.get();
    }

    private void migrate() throws SQLException {
        int current = 0;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT MAX(version) FROM pr_schema")) {
            if (result.next()) {
                current = result.getInt(1);
            }
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
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        }
    }

    private PlayerRankData loadExisting(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT rank_id, updated_at, first_joined_at FROM pr_players WHERE uuid=?")) {
            select.setString(1, uuid.toString());
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Player row was not found after creation race");
                }
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
                    future.complete(task.apply(connection));
                } catch (Throwable throwable) {
                    failures.incrementAndGet();
                    future.completeExceptionally(throwable);
                } finally {
                    completed.incrementAndGet();
                }
            });
            queueHighWater.accumulateAndGet(executor.getQueue().size(), Math::max);
        } catch (RejectedExecutionException exception) {
            failures.incrementAndGet();
            future.completeExceptionally(new IllegalStateException(
                    "PlexonRanks database queue is full or shutting down (depth=" + queueDepth()
                            + ", capacity=" + queueCapacity + ")", exception));
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
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            logger.warning("Database did not close cleanly: " + exception.getMessage());
        }
    }

    private static ThreadPoolExecutor createExecutor(String name, int queueCapacity) {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }
}
