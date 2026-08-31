package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.Rank;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class RankConfigEditor {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private final JavaPlugin plugin;
    private final ConfigManager configs;

    public RankConfigEditor(JavaPlugin plugin, ConfigManager configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public EditResult edit(String rankId, Consumer<ConfigurationSection> change) {
        return mutate(yaml -> {
            ConfigurationSection section = yaml.getConfigurationSection("ranks." + rankId);
            if (section == null) throw new IllegalArgumentException("Unknown rank: " + rankId);
            change.accept(section);
        });
    }

    public EditResult swapOrder(String rankId, int direction) {
        Rank current = configs.current().registry().byId(rankId).orElseThrow();
        Rank target = configs.current().registry().shift(current, direction).orElse(current);
        if (target.id().equals(current.id())) return new EditResult(true, List.of());
        return mutate(yaml -> {
            yaml.set("ranks." + current.id() + ".order", target.order());
            yaml.set("ranks." + target.id() + ".order", current.order());
        });
    }

    public EditResult create(String rankId) {
        String id = rankId.trim().toLowerCase();
        if (!VALID_ID.matcher(id).matches()) {
            return new EditResult(false, List.of("ID must match " + VALID_ID.pattern()));
        }
        if (configs.current().registry().byId(id).isPresent()) {
            return new EditResult(false, List.of("That rank ID already exists."));
        }
        int order = configs.current().registry().ordered().stream().mapToInt(Rank::order).max().orElse(-1) + 1;
        return mutate(yaml -> {
            String base = "ranks." + id;
            yaml.set(base + ".order", order);
            yaml.set(base + ".enabled", true);
            yaml.set(base + ".visible", true);
            yaml.set(base + ".default", false);
            yaml.set(base + ".display.name", "<white>" + id + "</white>");
            yaml.set(base + ".display.short-name", id);
            yaml.set(base + ".display.tag", "<dark_gray>[</dark_gray><white>" + id + "</white><dark_gray>]</dark_gray>");
            yaml.set(base + ".display.description", List.of("<gray>New progression rank.</gray>"));
            yaml.set(base + ".requirements", List.of());
            yaml.set(base + ".rewards", List.of());
            yaml.set(base + ".announce", true);
            yaml.set(base + ".menu.use-global-template", true);
        });
    }

    public EditResult duplicate(String sourceId, String newId) {
        String id = newId.trim().toLowerCase();
        if (!VALID_ID.matcher(id).matches() || configs.current().registry().byId(id).isPresent()) {
            return new EditResult(false, List.of("Choose a unique ID matching " + VALID_ID.pattern()));
        }
        int order = configs.current().registry().ordered().stream().mapToInt(Rank::order).max().orElse(-1) + 1;
        return mutate(yaml -> {
            ConfigurationSection source = yaml.getConfigurationSection("ranks." + sourceId);
            if (source == null) throw new IllegalArgumentException("Unknown rank: " + sourceId);
            ConfigurationSection target = yaml.createSection("ranks." + id);
            copySection(source, target);
            target.set("order", order);
            target.set("default", false);
            target.set("display.short-name", id);
        });
    }

    public EditResult delete(String rankId) {
        if (configs.current().registry().all().size() <= 1) {
            return new EditResult(false, List.of("The final configured rank cannot be deleted."));
        }
        if (configs.current().registry().defaultRank().id().equals(rankId)) {
            return new EditResult(false, List.of("Choose another default rank before deleting this rank."));
        }
        return mutate(yaml -> yaml.set("ranks." + rankId, null));
    }

    private EditResult mutate(Consumer<YamlConfiguration> mutation) {
        File original = configs.file("ranks.yml");
        File candidate = new File(original.getParentFile(), "ranks.yml.editing");
        File backup = new File(original.getParentFile(), "ranks.yml.before-edit");
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(original);
            mutation.accept(yaml);
            yaml.save(candidate);
            Files.copy(original.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            move(candidate, original);
            ReloadResult reload = configs.reload();
            if (!reload.success()) {
                move(backup, original);
                configs.reload();
                return new EditResult(false, reload.issues().stream().map(issue -> issue.source() + ": " + issue.message()).toList());
            }
            Files.deleteIfExists(backup.toPath());
            return new EditResult(true, reload.issues().stream().map(issue -> issue.source() + ": " + issue.message()).toList());
        } catch (Exception exception) {
            plugin.getLogger().warning("Rank editor change failed: " + exception.getMessage());
            try {
                if (backup.exists()) {
                    move(backup, original);
                    configs.reload();
                }
                Files.deleteIfExists(candidate.toPath());
            } catch (IOException ignored) {
            }
            return new EditResult(false, List.of(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    private static void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection child) {
                copySection(child, target.createSection(key));
            } else {
                target.set(key, value);
            }
        }
    }

    private static void move(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record EditResult(boolean success, List<String> details) {
        public EditResult {
            details = List.copyOf(details);
        }
    }
}

