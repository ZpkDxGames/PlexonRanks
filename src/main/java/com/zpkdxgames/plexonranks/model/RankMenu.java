package com.zpkdxgames.plexonranks.model;

import java.util.List;

public record RankMenu(
        boolean useGlobalTemplate,
        String material,
        int amount,
        int customModelData,
        boolean glow,
        String name,
        List<String> lore
) {
    public RankMenu {
        lore = List.copyOf(lore);
    }
}

