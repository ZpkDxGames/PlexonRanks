package com.zpkdxgames.plexonranks.requirement;

import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import org.bukkit.entity.Player;

public interface RequirementHandler {
    RequirementType type();

    RequirementProgress evaluate(Player player, RequirementDefinition definition);

    Consumption consume(Player player, RequirementDefinition definition);
}

