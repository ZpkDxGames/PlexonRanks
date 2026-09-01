package com.zpkdxgames.plexonranks.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAndListExpansionTest {
    @Test
    void expandsListTokensOnlyWhenTheyOccupyTheWholeLine() {
        List<String> result = ListPlaceholderExpander.expand(
                List.of("Title %rank%", "%requirements%", "Rewards: %rewards%", "%rewards%"),
                Map.of("%requirements%", List.of("Money", "XP"), "%rewards%", List.of("Key", "Permission")));

        assertEquals(List.of("Title %rank%", "Money", "XP", "Rewards: %rewards%", "Key", "Permission"), result);
    }

    @Test
    void componentPlaceholdersPreserveTheirOwnFormatting() {
        TextFormatter formatter = new TextFormatter(true, true);
        var component = formatter.component("<gray>Rank:</gray> %rank%", Map.of("rank", "&x&F&F&C&8&5&7Gold"));
        assertEquals("Rank: Gold", formatter.plain(component));
    }

    @Test
    void miniMessageTemplateCanContainLegacyRankValuesWithoutLeakingTags() {
        TextFormatter formatter = new TextFormatter(true, true);
        List<String> expanded = ListPlaceholderExpander.expand(
                List.of("<gray>Current:</gray> %rank%", "Status: %status%"),
                Map.of());

        var rankLine = formatter.component(expanded.getFirst(), Map.of("rank", "&8[&7Unranked&8]&r"));
        var statusLine = formatter.component(expanded.get(1),
                Map.of("status", "<aqua><bold>CURRENT</bold></aqua>"));

        assertEquals("Current: [Unranked]", formatter.plain(rankLine));
        assertEquals("Status: CURRENT", formatter.plain(statusLine));
        assertFalse(formatter.plain(rankLine).contains("<gray>"));
        assertFalse(formatter.plain(statusLine).contains("<aqua>"));
    }

    @Test
    void legacyFallbackAlsoWorksInDirectMiniMessageText() {
        TextFormatter formatter = new TextFormatter(true, true);
        assertEquals("Label: Value", formatter.plain(formatter.component("<gray>Label:</gray> &eValue")));
    }

    @Test
    void strictValidationRejectsUnclosedFormattingButAllowsUsageArguments() {
        TextFormatter formatter = new TextFormatter(true, true);
        assertFalse(formatter.valid("<gradient:#4158D0:#C850C0>Broken"));
        assertFalse(formatter.valid("<!italic><gradient:#4158D0:#C850C0>Broken"));
        assertTrue(formatter.valid("<!italic><gray>Item lore</gray>"));
        assertTrue(formatter.valid("<!italic><!bold>Item name"));
        assertTrue(formatter.valid("<yellow>/rank <player></yellow>"));
    }
}
