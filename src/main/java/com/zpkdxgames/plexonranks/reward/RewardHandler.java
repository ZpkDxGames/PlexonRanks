package com.zpkdxgames.plexonranks.reward;

import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RewardDefinition;
import com.zpkdxgames.plexonranks.model.RewardType;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RewardHandler {
    RewardType type();

    CompletableFuture<Void> execute(Player player, Rank rank, RewardDefinition reward, Map<String, String> placeholders);
}

