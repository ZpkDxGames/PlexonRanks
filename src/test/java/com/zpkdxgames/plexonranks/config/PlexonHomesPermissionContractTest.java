package com.zpkdxgames.plexonranks.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlexonHomesPermissionContractTest {
    private static final Pattern LIMIT = Pattern.compile("(?m)^\\s*- plexonhomes\\.limit\\.(\\d+)\\s*$");

    @Test
    void bundledLadderUsesAuthoritativePlexonHomesLimits() throws IOException {
        String yaml = bundledRanks();
        assertFalse(yaml.contains("essentials.sethome.multiple"));
        assertFalse(yaml.contains("plexonhomes.limit.unlimited"));

        assertTrue(section(yaml, "unranked", "newbie-1").contains("plexonhomes.limit.5"));
        assertTrue(section(yaml, "newbie-1", "newbie-2").contains("plexonhomes.limit.8"));
        assertTrue(section(yaml, "tinkerer-1", "tinkerer-2").contains("plexonhomes.limit.12"));
        assertTrue(section(yaml, "technician-1", "technician-2").contains("plexonhomes.limit.16"));
        assertTrue(section(yaml, "inventor-1", "inventor-2").contains("plexonhomes.limit.20"));
        assertTrue(section(yaml, "skilled-1", "skilled-2").contains("plexonhomes.limit.24"));
        assertTrue(section(yaml, "pro-1", "pro-2").contains("plexonhomes.limit.30"));

        Matcher matcher = LIMIT.matcher(yaml);
        java.util.ArrayList<Integer> limits = new java.util.ArrayList<>();
        while (matcher.find()) limits.add(Integer.parseInt(matcher.group(1)));
        assertEquals(List.of(5, 8, 12, 16, 20, 24, 30), limits);
    }

    @Test
    void unrelatedEssentialsRewardsRemainUntouched() throws IOException {
        String yaml = bundledRanks();
        assertTrue(yaml.contains("essentials.head"));
        assertTrue(yaml.contains("essentials.back"));
    }

    @Test
    void plexonHomesActionAccessIsNotDuplicatedIntoRankRewards() throws IOException {
        String yaml = bundledRanks();
        assertFalse(yaml.contains("plexonhomes.sethome"));
        assertFalse(yaml.contains("plexonhomes.use"));
        assertFalse(yaml.contains("plexonhomes.delete"));
        assertFalse(yaml.contains("plexonhomes.rename"));
        assertFalse(yaml.contains("plexonhomes.gui"));
    }

    private static String bundledRanks() throws IOException {
        try (InputStream stream = PlexonHomesPermissionContractTest.class.getResourceAsStream("/ranks.yml")) {
            if (stream == null) throw new IOException("Bundled ranks.yml is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String section(String yaml, String rank, String nextRank) {
        String startMarker = "  " + rank + ":";
        String endMarker = "  " + nextRank + ":";
        int start = yaml.indexOf(startMarker);
        int end = yaml.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, "Missing rank " + rank);
        assertTrue(end > start, "Missing next-rank boundary " + nextRank);
        return yaml.substring(start, end);
    }
}
