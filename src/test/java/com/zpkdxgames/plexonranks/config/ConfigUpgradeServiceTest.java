package com.zpkdxgames.plexonranks.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpgradeServiceTest {
    @Test
    void mergeUpdatesKnownDefaultsAddsNewKeysAndPreservesCustomValues() throws Exception {
        YamlConfiguration oldDefaults = yaml("""
                formatting:
                  legacy: true
                menu:
                  title: Old title
                """);
        YamlConfiguration newDefaults = yaml("""
                formatting:
                  legacy: false
                  minimessage: true
                menu:
                  title: New title
                """);
        YamlConfiguration current = yaml("""
                formatting:
                  legacy: true
                menu:
                  title: My custom title
                """);

        ConfigUpgradeService.mergeUpgrade(current, oldDefaults, newDefaults);

        assertFalse(current.getBoolean("formatting.legacy"));
        assertTrue(current.getBoolean("formatting.minimessage"));
        assertEquals("My custom title", current.getString("menu.title"));
    }

    @Test
    void onlyExactV1RankLoreIsMovedToTheGlobalTemplate() {
        List<String> legacyLore = List.of(
                "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "&7Status: %status%",
                "",
                "&7Your progression starts here.",
                "&7Use &e/rankup &7to begin climbing.",
                "",
                "&8Rank ID: &70",
                "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
        YamlConfiguration ranks = new YamlConfiguration();
        ranks.set("ranks.unranked.menu.use-global-template", false);
        ranks.set("ranks.unranked.menu.lore", legacyLore);
        ranks.set("ranks.custom.menu.use-global-template", false);
        ranks.set("ranks.custom.menu.lore", List.of("My custom lore"));

        assertEquals(1, ConfigUpgradeService.migrateKnownRankLores(ranks));
        assertTrue(ranks.getBoolean("ranks.unranked.menu.use-global-template"));
        assertFalse(ranks.contains("ranks.unranked.menu.lore"));
        assertFalse(ranks.getBoolean("ranks.custom.menu.use-global-template"));
        assertEquals(List.of("My custom lore"), ranks.getStringList("ranks.custom.menu.lore"));
    }

    private static YamlConfiguration yaml(String source) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(source);
        return configuration;
    }
}
