package com.zpkdxgames.plexonranks.reward;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit structural contracts for reward safety. Bukkit/Paper ItemStack construction
 * requires a live registry in Paper 26.2, so full-inventory behavior remains an RC runtime gate.
 */
class RewardSafetyTest {
    @Test
    void itemOverflowHasNoWorldDropFallback() throws Exception {
        String source = source();
        assertFalse(source.contains("dropItem("));
        assertFalse(source.contains("dropItemNaturally("));
        assertTrue(source.contains("Item reward overflow; world-drop fallback is prohibited"));
        assertTrue(source.contains("Player inventory cannot safely receive all item rewards"));
    }

    @Test
    void externalCommandsAreExplicitlySeparatedFromReversibleRewards() throws Exception {
        String source = source();
        int reversible = source.indexOf("ReversibleRewardBatch executeReversible");
        int commands = source.indexOf("void executeCommands");
        assertTrue(reversible >= 0);
        assertTrue(commands > reversible);
        assertTrue(source.contains("Irreversible external command boundary; must be the final reward stage"));
    }

    @Test
    void reversibleBatchRollsBackInReverseOrder() throws Exception {
        String source = source();
        assertTrue(source.contains("for (int index = rollbacks.size() - 1; index >= 0; index--)"));
        assertTrue(source.contains("if (rolledBack) return"));
    }

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/zpkdxgames/plexonranks/reward/RewardEngine.java"));
    }
}
