package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.RankRegistry;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

public record ConfigSnapshot(
        YamlConfiguration config,
        YamlConfiguration ranks,
        YamlConfiguration menus,
        YamlConfiguration messages,
        RankRegistry registry,
        List<ValidationIssue> validationIssues
) {
    public ConfigSnapshot {
        validationIssues = List.copyOf(validationIssues);
    }

    public String storageFingerprint() {
        return config.getString("storage.type", "SQLITE") + ":"
                + config.getString("storage.sqlite.file", "database.db");
    }
}

