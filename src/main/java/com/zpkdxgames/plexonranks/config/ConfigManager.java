package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.RankRegistry;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final RankParser rankParser = new RankParser();
    private final ConfigurationValidator validator = new ConfigurationValidator();
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();
    private volatile ConfigSnapshot current;
    private volatile TextFormatter formatter;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaults() throws Exception {
        plugin.getDataFolder().mkdirs();
        saveIfMissing("config.yml");
        saveIfMissing("ranks.yml");
        saveIfMissing("menus.yml");
        saveIfMissing("messages.yml");
        ConfigUpgradeService.UpgradeResult upgrade = new ConfigUpgradeService(plugin).upgradeIfNeeded();
        if (upgrade.upgraded()) {
            plugin.getLogger().info("Upgraded PlexonRanks configuration to schema 2. Previous files: "
                    + upgrade.backupDirectory().getFileName());
        }
    }

    public ConfigSnapshot loadInitial() {
        LoadAttempt attempt = parse();
        if (attempt.snapshot == null || hasErrors(attempt.issues)) {
            throw new IllegalStateException("Invalid PlexonRanks configuration: " + summarize(attempt.issues));
        }
        this.current = attempt.snapshot;
        this.formatter = attempt.formatter;
        return current;
    }

    public ReloadResult reload() {
        String oldStorage = current == null ? "" : current.storageFingerprint();
        LoadAttempt attempt = parse();
        if (attempt.snapshot == null || hasErrors(attempt.issues)) {
            return new ReloadResult(false, false, attempt.issues);
        }
        boolean restartRequired = !oldStorage.isBlank() && !oldStorage.equals(attempt.snapshot.storageFingerprint());
        this.current = attempt.snapshot;
        this.formatter = attempt.formatter;
        reloadListeners.forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Post-reload listener failed: " + exception.getMessage());
            }
        });
        return new ReloadResult(true, restartRequired, attempt.issues);
    }

    public ReloadResult validateCandidate() {
        LoadAttempt attempt = parse();
        return new ReloadResult(attempt.snapshot != null && !hasErrors(attempt.issues), false, attempt.issues);
    }

    public ConfigSnapshot current() {
        ConfigSnapshot snapshot = current;
        if (snapshot == null) {
            throw new IllegalStateException("Configuration has not been loaded");
        }
        return snapshot;
    }

    public TextFormatter formatter() {
        TextFormatter active = formatter;
        if (active == null) {
            throw new IllegalStateException("Formatting engine has not been loaded");
        }
        return active;
    }

    public File file(String name) {
        return new File(plugin.getDataFolder(), name);
    }

    public void onReload(Runnable listener) {
        reloadListeners.add(listener);
    }

    private LoadAttempt parse() {
        List<ValidationIssue> issues = new ArrayList<>();
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file("config.yml"));
            YamlConfiguration ranksYaml = YamlConfiguration.loadConfiguration(file("ranks.yml"));
            YamlConfiguration menus = YamlConfiguration.loadConfiguration(file("menus.yml"));
            YamlConfiguration messages = YamlConfiguration.loadConfiguration(file("messages.yml"));
            RuntimeSettings settings = RuntimeSettings.parse(config, menus);
            TextFormatter candidateFormatter = new TextFormatter(
                    settings.miniMessage(),
                    settings.legacyAmpersandSupport());
            RankParser.ParseResult parsed = rankParser.parse(ranksYaml);
            issues.addAll(parsed.issues());
            issues.addAll(validator.validate(config, ranksYaml, menus, messages, parsed.ranks(), candidateFormatter));
            if (parsed.ranks().isEmpty() || hasErrors(issues)) {
                return new LoadAttempt(null, candidateFormatter, issues);
            }
            RankRegistry registry = new RankRegistry(parsed.ranks());
            return new LoadAttempt(new ConfigSnapshot(
                    config,
                    ranksYaml,
                    menus,
                    messages,
                    settings,
                    registry,
                    issues
            ), candidateFormatter, issues);
        } catch (Exception exception) {
            plugin.getLogger().severe("Configuration parse failed: " + exception.getMessage());
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "configuration", exception.getMessage()));
            return new LoadAttempt(null, formatter == null ? new TextFormatter(true) : formatter, issues);
        }
    }

    private void saveIfMissing(String resource) {
        if (!file(resource).exists()) {
            plugin.saveResource(resource, false);
        }
    }

    private static boolean hasErrors(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
    }

    private static String summarize(List<ValidationIssue> issues) {
        return issues.stream().limit(5).map(issue -> issue.source() + ": " + issue.message()).reduce((a, b) -> a + "; " + b).orElse("unknown error");
    }

    private record LoadAttempt(ConfigSnapshot snapshot, TextFormatter formatter, List<ValidationIssue> issues) {
    }
}
