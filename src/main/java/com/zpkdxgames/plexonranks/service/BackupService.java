package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BackupService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneOffset.UTC);
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final DatabaseManager database;

    public BackupService(JavaPlugin plugin, ConfigManager configs, DatabaseManager database) {
        this.plugin = plugin;
        this.configs = configs;
        this.database = database;
    }

    public CompletableFuture<Path> create() {
        String stamp = STAMP.format(Instant.now());
        Path directory = plugin.getDataFolder().toPath().resolve("backups").resolve("backup-" + stamp);
        Path dbTarget = directory.resolve("database.db");
        return database.backupTo(dbTarget).thenApply(path -> {
            try {
                for (String name : List.of("config.yml", "ranks.yml", "menus.yml", "messages.yml")) {
                    Files.copy(configs.file(name).toPath(), directory.resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
                }
                enforceRetention();
                return directory;
            } catch (IOException exception) {
                throw new IllegalStateException("Could not copy configuration into backup", exception);
            }
        });
    }

    private void enforceRetention() throws IOException {
        int keep = Math.max(1, configs.current().config().getInt("backup.keep", 10));
        Path root = plugin.getDataFolder().toPath().resolve("backups");
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> directories;
        try (var stream = Files.list(root)) {
            directories = stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (Path old : directories.stream().skip(keep).toList()) {
            try (var walk = Files.walk(old)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
