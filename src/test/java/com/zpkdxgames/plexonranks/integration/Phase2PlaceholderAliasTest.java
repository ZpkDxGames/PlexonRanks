package com.zpkdxgames.plexonranks.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class Phase2PlaceholderAliasTest {
    @Test
    void premiumAliasesPreserveExistingPlaceholderContracts() {
        assertEquals("rank_name", PlexonRanksExpansion.alias("rank"));
        assertEquals("rank_name", PlexonRanksExpansion.alias("rank_display"));
        assertEquals("rank_order", PlexonRanksExpansion.alias("rank_number"));
        assertEquals("next_name", PlexonRanksExpansion.alias("next_rank"));
        assertEquals("progress_percent", PlexonRanksExpansion.alias("progress"));
        assertEquals("requirement_money_current", PlexonRanksExpansion.alias("money_current"));
        assertEquals("requirement_money_required", PlexonRanksExpansion.alias("money_required"));
        assertEquals("requirement_playtime_current", PlexonRanksExpansion.alias("playtime_current"));
        assertEquals("requirement_playtime_required", PlexonRanksExpansion.alias("playtime_required"));
        assertEquals("requirement_xp_current", PlexonRanksExpansion.alias("xp_current"));
        assertEquals("requirement_xp_required", PlexonRanksExpansion.alias("xp_required"));
        assertEquals("can_rankup", PlexonRanksExpansion.alias("can_rankup"));
    }
}
