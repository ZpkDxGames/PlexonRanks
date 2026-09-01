package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.ValidationIssue;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledConfigurationTest {
    @Test
    void everyBundledConfigurationUsesSchemaTwoAndRanksParseCleanly() {
        for (String name : List.of("config.yml", "ranks.yml", "menus.yml", "messages.yml")) {
            assertEquals(2, load(name).getInt("schema-version"), name);
        }

        RankParser.ParseResult result = new RankParser().parse(load("ranks.yml"));
        assertEquals(36, result.ranks().size());
        assertFalse(result.issues().stream()
                .anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR),
                () -> "Bundled ranks contain errors: " + result.issues());
        assertTrue(result.ranks().stream().allMatch(rank -> rank.menu().useGlobalTemplate()));
    }

    @Test
    void bundledConfigurationPassesTheSameValidationUsedAtStartup() {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration ranksYaml = load("ranks.yml");
        YamlConfiguration menus = load("menus.yml");
        YamlConfiguration messages = load("messages.yml");
        RankParser.ParseResult parsed = new RankParser().parse(ranksYaml);
        TextFormatter formatter = new TextFormatter(
                config.getBoolean("formatting.minimessage", true),
                config.getBoolean("formatting.legacy-ampersand-support", true));

        List<ValidationIssue> issues = new ConfigurationValidator().validate(
                config, ranksYaml, menus, messages, parsed.ranks(), formatter);

        assertFalse(issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR),
                () -> "Bundled configuration fails startup validation: " + issues);
    }

    private static YamlConfiguration load(String name) {
        try (var stream = Objects.requireNonNull(
                BundledConfigurationTest.class.getClassLoader().getResourceAsStream(name), name);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new AssertionError("Could not load " + name, exception);
        }
    }
}
