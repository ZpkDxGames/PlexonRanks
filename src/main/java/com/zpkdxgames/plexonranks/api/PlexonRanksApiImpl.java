package com.zpkdxgames.plexonranks.api;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankHistoryEntry;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.service.RankService;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    public Optional<Rank> getRank(UUID playerId) { return ranks.current(playerId); }

    @Override
    public Optional<Rank> getNextRank(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player == null) return getRank(playerId).flatMap(configs.current().registry()::next);
        return ranks.next(player);
    }

    @Override
    public Optional<Rank> getRankById(String rankId) { return configs.current().registry().byId(rankId); }

    @Override
    public List<Rank> getRanks() { return List.copyOf(configs.current().registry().ordered()); }

    @Override
    public boolean canRankup(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player == null || !Bukkit.isPrimaryThread() || !ranks.loaded(playerId)) return false;
        return ranks.next(player)
                .map(next -> requirements.evaluate(player, next.requirements()).stream().allMatch(RequirementProgress::complete))
                .orElse(false);
    }

    @Override
    public Optional<RankView> currentView(UUID playerId) { return ranks.current(playerId).map(this::view); }

    @Override
    public Optional<RankView> nextView(UUID playerId) { return getNextRank(playerId).map(this::view); }

    @Override
    public List<RankView> getRankViews() {
        return configs.current().registry().ordered().stream().map(this::view).toList();
    }

    @Override
    public Optional<ProgressionView> progression(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player == null || !Bukkit.isPrimaryThread() || !ranks.loaded(playerId)) return Optional.empty();
        Rank current = ranks.current(playerId).orElse(configs.current().registry().defaultRank());
        Optional<Rank> next = configs.current().registry().nextAccessible(current, player::hasPermission);
        if (next.isEmpty()) {
            return Optional.of(new ProgressionView(playerId, view(current), Optional.empty(), List.of(), 1.0, false, true));
        }
        List<RequirementProgress> progress = requirements.evaluate(player, next.get().requirements());
        List<RequirementView> views = progress.stream().map(this::requirement).toList();
        double overall = RequirementEngine.overallProgress(progress);
        return Optional.of(new ProgressionView(playerId, view(current), Optional.of(view(next.get())), views,
                overall, progress.stream().allMatch(RequirementProgress::complete), false));
    }

    @Override
    public CompletableFuture<List<RankHistoryEntry>> history(UUID playerId, int limit) {
        return ranks.history(playerId, limit);
    }

    private RankView view(Rank rank) {
        return new RankView(rank.id(), rank.order(), rank.tier(), rank.display().name(), rank.display().shortName(),
                rank.display().tag(), configs.current().registry().terminalRank().id().equalsIgnoreCase(rank.id()));
    }

    private RequirementView requirement(RequirementProgress progress) {
        return new RequirementView(progress.definition().type().name(), progress.current(), progress.required(),
                progress.normalized(), progress.complete(), progress.placeholders());
    }
}
