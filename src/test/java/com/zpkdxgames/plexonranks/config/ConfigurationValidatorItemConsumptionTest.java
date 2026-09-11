package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationValidatorItemConsumptionTest {
    @Test
    void rejectsRepeatedConsumableMaterialThatWouldOverbookInventory() {
        RequirementDefinition first = item("DIAMOND", 3, true);
        RequirementDefinition second = item("DIAMOND", 4, true);
        RequirementDefinition visibleOnly = item("DIAMOND", 9, false);

        assertEquals(Set.of(Material.DIAMOND),
                ConfigurationValidator.duplicateConsumableItemMaterials(List.of(first, second, visibleOnly)));
    }

    @Test
    void allowsRepeatedNonConsumableChecksAndDistinctConsumableMaterials() {
        RequirementDefinition visibleOne = item("DIAMOND", 3, false);
        RequirementDefinition visibleTwo = item("DIAMOND", 4, false);
        RequirementDefinition diamonds = item("DIAMOND", 2, true);
        RequirementDefinition emeralds = item("EMERALD", 2, true);

        assertTrue(ConfigurationValidator.duplicateConsumableItemMaterials(
                List.of(visibleOne, visibleTwo, diamonds, emeralds)).isEmpty());
    }

    private static RequirementDefinition item(String material, int amount, boolean consume) {
        return new RequirementDefinition(
                RequirementType.ITEM,
                amount,
                consume,
                Map.of("material", material, "amount", amount, "consume", consume));
    }
}
