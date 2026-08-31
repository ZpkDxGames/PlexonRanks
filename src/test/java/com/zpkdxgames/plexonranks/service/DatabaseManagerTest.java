package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
}

