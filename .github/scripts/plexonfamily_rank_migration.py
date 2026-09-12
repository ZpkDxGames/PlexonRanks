from pathlib import Path
import re

ROOT = Path('.')
RANKS = ROOT / 'src/main/resources/ranks.yml'
HOMES_TEST = ROOT / 'src/test/java/com/zpkdxgames/plexonranks/config/PlexonHomesPermissionContractTest.java'
FAMILY_TEST = ROOT / 'src/test/java/com/zpkdxgames/plexonranks/config/PlexonFamilyPermissionContractTest.java'
POM = ROOT / 'pom.xml'
CHANGELOG = ROOT / 'CHANGELOG.md'
RELEASE_NOTES = ROOT / '.release/RELEASE_NOTES_3.0.2.md'

ranks = RANKS.read_text()
replacements = [
    ('      - essentials.back.ondeath\n', '      - plexontravel.back\n'),
    ('      - essentials.back\n', '      - plexontravel.back\n'),
    ('      - essentials.workbench\n', '      - plexonutility.workbench\n'),
    ('      - essentials.disposal\n', '      - plexonutility.trash\n'),
    ('      - essentials.enderchest\n', '      - plexonutility.enderchest\n'),
    ('      - essentials.feed\n', '      - plexonutility.feed\n'),
    ("      - '&8• &e/disposal &7command'\n", "      - '&8• &e/trash &7command'\n"),
    ("      - '&8• &bColored Chat'\n", "      - '&8• &bChat Formatting'\n"),
]
for old, new in replacements:
    count = ranks.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one occurrence of {old.strip()!r}; found {count}')
    ranks = ranks.replace(old, new)

chat_block = (
    '      - essentials.chat.color\n'
    '      - venturechat.color\n'
    '      - venturechat.color.legacy\n'
)
if ranks.count(chat_block) != 1:
    raise SystemExit('Expected exactly one legacy chat-formatting permission block')
ranks = ranks.replace(chat_block, '      - plexonchats.formatting\n')
RANKS.write_text(ranks)

homes_test = HOMES_TEST.read_text()
old = '        assertTrue(yaml.contains("essentials.back"));\n'
new = '        assertTrue(yaml.contains("essentials.hat"));\n'
if homes_test.count(old) != 1:
    raise SystemExit('Unexpected PlexonHomes regression-test shape')
HOMES_TEST.write_text(homes_test.replace(old, new))

pom = POM.read_text()
old_version = '<artifactId>PlexonRanks</artifactId><version>3.0.1</version>'
new_version = '<artifactId>PlexonRanks</artifactId><version>3.0.2</version>'
if pom.count(old_version) != 1:
    raise SystemExit('Expected PlexonRanks version 3.0.1')
POM.write_text(pom.replace(old_version, new_version))

family_test = '''package com.zpkdxgames.plexonranks.config;

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
    private static final Pattern ESSENTIALS = Pattern.compile("(?m)^\\\\s*- (essentials\\\\.[a-z0-9_.-]+)\\\\s*$");

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
'''
FAMILY_TEST.write_text(family_test)

notes = '''# PlexonRanks 3.0.2

Stable PlexonFamily permission-alignment update for the bundled rank ladder.

## Replaced plugin ownership

- `/workbench`: `essentials.workbench` -> `plexonutility.workbench`
- `/disposal`: `essentials.disposal` -> canonical PlexonUtility `/trash` with `plexonutility.trash`
- `/enderchest`: `essentials.enderchest` -> `plexonutility.enderchest`
- `/feed`: `essentials.feed` -> `plexonutility.feed`
- `/back` and back-after-death: Essentials nodes -> `plexontravel.back`
- colored chat: Essentials/VentureChat nodes -> `plexonchats.formatting`

## Home limits

Home capacity remains owned by PlexonHomes through `plexonhomes.limit.<N>`. PlexonUtility may surface PlexonHomes in its utility experience, but it does not define a separate home-limit permission contract.

## Preserved compatibility

Essentials rewards without a current PlexonFamily equivalent remain unchanged: head, hat, compass, depth, condense, getpos, ext, and keepxp. Existing iDisguise, PlexonKeys, and claim-block rewards are unchanged.
'''
RELEASE_NOTES.write_text(notes)

changelog = CHANGELOG.read_text()
marker = '# Changelog\n\n'
section = '''## 3.0.2 — PlexonFamily permission alignment

### Family-owned rank rewards

- Replaced Essentials utility permissions with active PlexonUtility nodes for `/workbench`, `/trash`, `/enderchest`, and `/feed`.
- Replaced Essentials `/back` and `/back`-after-death grants with `plexontravel.back`; PlexonTravel owns persisted back history and death capture.
- Replaced Essentials/VentureChat colored-chat grants with `plexonchats.formatting`.
- Preserved the PlexonHomes numeric home-limit ladder because PlexonHomes, not PlexonUtility, remains the authoritative home-limit owner.
- Preserved unrelated Essentials rewards that do not yet have a PlexonFamily replacement.

### Validation

- Added regression coverage that rejects retired Essentials/VentureChat nodes while requiring the exact PlexonFamily replacement nodes.
- Updated the PlexonHomes contract test so `/back` is no longer treated as an intentionally retained Essentials permission.

'''
if not changelog.startswith(marker):
    raise SystemExit('Unexpected CHANGELOG.md header')
if '## 3.0.2 — PlexonFamily permission alignment' not in changelog:
    CHANGELOG.write_text(marker + section + changelog[len(marker):])

required = {
    'plexonutility.workbench', 'plexonutility.trash', 'plexonutility.enderchest',
    'plexonutility.feed', 'plexontravel.back', 'plexonchats.formatting',
    'plexonhomes.limit.5', 'plexonhomes.limit.8', 'plexonhomes.limit.12',
    'plexonhomes.limit.16', 'plexonhomes.limit.20', 'plexonhomes.limit.24',
    'plexonhomes.limit.30'
}
retired = {
    'essentials.workbench', 'essentials.disposal', 'essentials.enderchest',
    'essentials.feed', 'essentials.back', 'essentials.back.ondeath',
    'essentials.chat.color', 'venturechat.color', 'venturechat.color.legacy'
}
for value in required:
    if value not in ranks:
        raise SystemExit(f'Missing required permission: {value}')
for value in retired:
    if value in ranks:
        raise SystemExit(f'Retired permission remains: {value}')
if 'plexonutility.limit.' in ranks or 'plexonutility.homes.' in ranks:
    raise SystemExit('Invented PlexonUtility home-limit node detected')

allowed_essentials = {
    'essentials.head', 'essentials.hat', 'essentials.compass', 'essentials.depth',
    'essentials.condense', 'essentials.getpos', 'essentials.ext', 'essentials.keepxp'
}
actual_essentials = set(re.findall(r'^\\s*- (essentials\\.[a-z0-9_.-]+)\\s*$', ranks, re.M))
if actual_essentials != allowed_essentials:
    raise SystemExit(f'Unexpected remaining Essentials permissions: {sorted(actual_essentials)}')

print('PlexonFamily rank migration audit passed')
