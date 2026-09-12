package com.zpkdxgames.plexonranks.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlexonFamilyPermissionContractTest {
    private static final Pattern ESSENTIALS = Pattern.compile("(?m)^\\s*- (essentials\\.[a-z0-9_.-]+)\\s*$");

    @Test
    void replacedCapabilitiesUsePlexonFamilyPermissions() throws IOException {
        String yaml = bundledRanks();
        for (String required : Set.of(
                "plexonutility.workbench",
                "plexonutility.trash",
                "plexonutility.enderchest",
                "plexonutility.feed",
                "plexontravel.back",
                "plexonchats.formatting")) {
            assertTrue(yaml.contains(required), "Missing family permission " + required);
        }

        for (String retired : Set.of(
                "essentials.workbench",
                "essentials.disposal",
                "essentials.enderchest",
                "essentials.feed",
                "essentials.back",
                "essentials.back.ondeath",
                "essentials.chat.color",
                "venturechat.color",
                "venturechat.color.legacy")) {
            assertFalse(yaml.contains(retired), "Retired permission remains: " + retired);
        }
    }

    @Test
    void onlyUnsupportedEssentialsRewardsRemain() throws IOException {
        String yaml = bundledRanks();
        Set<String> expected = Set.of(
                "essentials.head",
                "essentials.hat",
                "essentials.compass",
                "essentials.depth",
                "essentials.condense",
                "essentials.getpos",
                "essentials.ext",
                "essentials.keepxp");
        java.util.HashSet<String> actual = new java.util.HashSet<>();
        Matcher matcher = ESSENTIALS.matcher(yaml);
        while (matcher.find()) actual.add(matcher.group(1));
        assertEquals(expected, actual);
    }

    @Test
    void homeLimitsStayWithPlexonHomes() throws IOException {
        String yaml = bundledRanks();
        assertTrue(yaml.contains("plexonhomes.limit.5"));
        assertTrue(yaml.contains("plexonhomes.limit.30"));
        assertFalse(yaml.contains("plexonutility.limit."));
        assertFalse(yaml.contains("plexonutility.homes."));
    }

    private static String bundledRanks() throws IOException {
        try (InputStream stream = PlexonFamilyPermissionContractTest.class.getResourceAsStream("/ranks.yml")) {
            if (stream == null) throw new IOException("Bundled ranks.yml is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
