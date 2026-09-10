package com.zpkdxgames.plexonranks.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RewardSafetyTest {
    @Test
    void fullInventoryRejectsRewardWithoutMutation() {
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < storage.length; i++) storage[i] = new ItemStack(Material.STONE, 64);
        ItemStack[] before = cloneStorage(storage);

        assertFalse(RewardEngine.mergeInto(storage, new ItemStack(Material.DIAMOND, 1)));
        for (int i = 0; i < storage.length; i++) assertEquals(before[i], storage[i]);
    }

    @Test
    void partialSimilarStackReceivesRewardWithoutExtraSlot() {
        ItemStack[] storage = new ItemStack[36];
        storage[0] = new ItemStack(Material.DIAMOND, 60);
        assertTrue(RewardEngine.mergeInto(storage, new ItemStack(Material.DIAMOND, 4)));
        assertEquals(64, storage[0].getAmount());
        for (int i = 1; i < storage.length; i++) assertEquals(null, storage[i]);
    }

    @Test
    void emptySlotsAreSimulatedDeterministically() {
        ItemStack[] storage = new ItemStack[2];
        assertTrue(RewardEngine.mergeInto(storage, new ItemStack(Material.DIAMOND, 64)));
        assertEquals(Material.DIAMOND, storage[0].getType());
        assertEquals(64, storage[0].getAmount());
        assertEquals(null, storage[1]);
    }

    private static ItemStack[] cloneStorage(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }
}
