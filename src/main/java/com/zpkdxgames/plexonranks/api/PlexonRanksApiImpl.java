package com.zpkdxgames.plexonranks.api;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.service.RankService;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlexonRanksApiImpl implements PlexonRanksAPI {
    private final ConfigManager configs;
    private final RankService ranks;
    private final RequirementEngine requirements;

    public PlexonRanksApiImpl(ConfigManager configs, RankService ranks, RequirementEngine requirements) {
        this.configs = configs;
        this.ranks = ranks;
        this.requirements = requirements;
    }

    @Override
    public Optional<Rank> getRank(UUID playerId) {
        return ranks.current(playerId);
    }

    @Override
    public Optional<Rank> getNextRank(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return getRank(playerId).flatMap(configs.current().registry()::next);
        }
        return ranks.next(player);
    }

    @Override
    public Optional<Rank> getRankById(String rankId) {
        return configs.current().registry().byId(rankId);
    }

    @Override
    public List<Rank> getRanks() {
        return List.copyOf(configs.current().registry().ordered());
    }

    @Override
    public boolean canRankup(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player == null || !Bukkit.isPrimaryThread()) {
            return false;
        }
        return ranks.next(player)
                .map(next -> requirements.evaluate(player, next.requirements()).stream().allMatch(progress -> progress.complete()))
                .orElse(false);
    }
}

