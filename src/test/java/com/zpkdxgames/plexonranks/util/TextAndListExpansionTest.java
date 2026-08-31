package com.zpkdxgames.plexonranks.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextAndListExpansionTest {
    @Test
    void expandsListTokensOnlyWhenTheyOccupyTheWholeLine() {
        List<String> result = ListPlaceholderExpander.expand(
                List.of("Title %rank%", "%requirements%", "Rewards: %rewards%", "%rewards%"),
                Map.of("rank", "Newbie I"),
                Map.of("%requirements%", List.of("Money", "XP"), "%rewards%", List.of("Key", "Permission")));

        assertEquals(List.of("Title Newbie I", "Money", "XP", "Rewards: %rewards%", "Key", "Permission"), result);
    }

    @Test
    void componentPlaceholdersPreserveTheirOwnFormatting() {
        TextFormatter formatter = new TextFormatter(true);
        var component = formatter.component("<gray>Rank:</gray> %rank%", Map.of("rank", "&x&F&F&C&8&5&7Gold"));
        assertEquals("Rank: Gold", formatter.plain(component));
    }
}

