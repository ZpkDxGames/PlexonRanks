package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.util.TextFormatter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

final class MenuItems {
    private MenuItems() {
    }

    static ItemStack create(TextFormatter formatter, String materialName, int amount, String name, List<String> lore,
                            boolean glow, int customModelData, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(formatter.component(name == null ? " " : name, placeholders));
        meta.lore(formatter.components(lore, placeholders));
        meta.setEnchantmentGlintOverride(glow);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}

