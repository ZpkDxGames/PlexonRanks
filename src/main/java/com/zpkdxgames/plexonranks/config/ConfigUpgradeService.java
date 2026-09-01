package com.zpkdxgames.plexonranks.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ConfigUpgradeService {
    static final int CURRENT_SCHEMA = 2;
    private static final List<String> FILES = List.of("config.yml", "ranks.yml", "menus.yml", "messages.yml");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private static final List<String> LEGACY_STANDARD_RANK_LORE = List.of(
            "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&7Status: %status%",
            "",
            "&f&lRequirements",
            "&8• &7Money: &e$%money%",
            "&8• &7XP Levels: &e%xp%",
            "&8• &7Playtime: &e%playtime% min",
            "",
            "&f&lRewards",
            "%rewards%",
            "",
            "&8Rank ID: &7%rank_id%",
            "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    );
    private static final List<String> LEGACY_UNRANKED_LORE = List.of(
            "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&7Status: %status%",
            "",
            "&7Your progression starts here.",
            "&7Use &e/rankup &7to begin climbing.",
            "",
            "&8Rank ID: &70",
            "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    );

    private final JavaPlugin plugin;

    ConfigUpgradeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    UpgradeResult upgradeIfNeeded() throws Exception {
        Map<String, YamlConfiguration> loaded = new LinkedHashMap<>();
        boolean outdated = false;
        for (String name : FILES) {
            YamlConfiguration yaml = load(new File(plugin.getDataFolder(), name));
            loaded.put(name, yaml);
            outdated |= yaml.getInt("schema-version", 1) < CURRENT_SCHEMA;
        }
        if (!outdated) return new UpgradeResult(false, null);

        Path backup = plugin.getDataFolder().toPath().resolve("migration-backups")
                .resolve("v1-to-v2-" + STAMP.format(Instant.now()));
        Files.createDirectories(backup);
        for (String name : FILES) {
            Files.copy(plugin.getDataFolder().toPath().resolve(name), backup.resolve(name),
                    StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        }

        Map<String, Path> candidates = new LinkedHashMap<>();
        for (Map.Entry<String, YamlConfiguration> entry : loaded.entrySet()) {
            String name = entry.getKey();
            YamlConfiguration current = entry.getValue();
            if (current.getInt("schema-version", 1) >= CURRENT_SCHEMA) continue;
            if (name.equals("ranks.yml")) {
                migrateKnownRankLores(current);
            } else {
                mergeUpgrade(current, resource("migration/v1/" + name), resource(name));
            }
            current.set("schema-version", CURRENT_SCHEMA);
            Path candidate = plugin.getDataFolder().toPath().resolve(name + ".v2-upgrade");
            current.save(candidate.toFile());
            candidates.put(name, candidate);
        }
        for (Map.Entry<String, Path> entry : candidates.entrySet()) {
            move(entry.getValue(), plugin.getDataFolder().toPath().resolve(entry.getKey()));
        }
        return new UpgradeResult(true, backup);
    }

    static void mergeUpgrade(YamlConfiguration current, YamlConfiguration oldDefaults,
                             YamlConfiguration newDefaults) {
        for (String path : newDefaults.getKeys(true)) {
            Object newValue = newDefaults.get(path);
            if (newValue instanceof ConfigurationSection) continue;
            if (!current.contains(path)) {
                current.set(path, newValue);
                continue;
            }
            Object oldValue = oldDefaults.get(path);
            if (oldValue != null && equivalent(current.get(path), oldValue)) {
                current.set(path, newValue);
            }
        }
    }

    static int migrateKnownRankLores(YamlConfiguration ranks) {
        ConfigurationSection root = ranks.getConfigurationSection("ranks");
        if (root == null) return 0;
        int migrated = 0;
        for (String id : root.getKeys(false)) {
            String base = "ranks." + id + ".menu";
            List<String> lore = ranks.getStringList(base + ".lore");
            if (lore.equals(LEGACY_STANDARD_RANK_LORE) || lore.equals(LEGACY_UNRANKED_LORE)) {
                ranks.set(base + ".use-global-template", true);
                ranks.set(base + ".lore", null);
                migrated++;
            }
        }
        return migrated;
    }

    private YamlConfiguration resource(String name) {
        InputStream stream = plugin.getResource(name);
        if (stream == null) throw new IllegalStateException("Missing bundled configuration resource: " + name);
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load bundled configuration resource: " + name, exception);
        }
    }

    private static YamlConfiguration load(File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    private static boolean equivalent(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private static void move(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record UpgradeResult(boolean upgraded, Path backupDirectory) {
    }
}
