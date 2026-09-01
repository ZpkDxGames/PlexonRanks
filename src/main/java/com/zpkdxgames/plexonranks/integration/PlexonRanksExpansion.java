package com.zpkdxgames.plexonranks.integration;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RenderService;
import com.zpkdxgames.plexonranks.util.NumberFormats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PlexonRanksExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final RenderService render;

    public PlexonRanksExpansion(JavaPlugin plugin, ConfigManager configs, RankService ranks, RenderService render) {
        this.plugin = plugin;
        this.configs = configs;
        this.ranks = ranks;
        this.render = render;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "plexonranks";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ZpkDxGames";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null || !ranks.loaded(player.getUniqueId())) return "";
        Rank current = ranks.current(player.getUniqueId()).orElse(configs.current().registry().defaultRank());
        Optional<Rank> next = configs.current().registry().nextAccessible(current, player::hasPermission);
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "rank_id" -> current.id();
            case "rank_order" -> String.valueOf(current.order());
            case "rank_name" -> current.display().name();
            case "rank_short_name" -> current.display().shortName();
            case "rank_tag" -> current.display().tag();
            case "rank_name_plain" -> formatted(current.display().name(), "PLAIN");
            case "rank_tag_plain" -> formatted(current.display().tag(), "PLAIN");
            case "rank_name_mm", "rank_name_minimessage" -> formatted(current.display().name(), "MINIMESSAGE");
            case "rank_tag_mm", "rank_tag_minimessage" -> formatted(current.display().tag(), "MINIMESSAGE");
            case "rank_name_legacy" -> formatted(current.display().name(), "LEGACY");
            case "rank_tag_legacy" -> formatted(current.display().tag(), "LEGACY");
            case "next_id" -> next.map(Rank::id).orElse("");
            case "next_name" -> next.map(rank -> rank.display().name()).orElse("");
            case "next_short_name" -> next.map(rank -> rank.display().shortName()).orElse("");
            case "next_name_plain" -> next.map(rank -> formatted(rank.display().name(), "PLAIN")).orElse("");
            case "next_name_mm", "next_name_minimessage" -> next.map(rank -> formatted(rank.display().name(), "MINIMESSAGE")).orElse("");
            case "next_name_legacy" -> next.map(rank -> formatted(rank.display().name(), "LEGACY")).orElse("");
            case "is_max_rank" -> String.valueOf(next.isEmpty());
            case "progress_percent" -> next.map(rank -> NumberFormats.number(
                    RequirementEngine.overallProgress(render.progress(player, rank)) * 100.0)).orElse("100");
            default -> requirementPlaceholder(player, next, key);
        };
    }

    private String requirementPlaceholder(Player player, Optional<Rank> next, String key) {
        if (next.isEmpty() || !key.startsWith("requirement_")) return null;
        String[] parts = key.split("_");
        if (parts.length < 3) return null;
        RequirementType type = switch (parts[1]) {
            case "money" -> RequirementType.MONEY;
            case "xp" -> RequirementType.XP_LEVELS;
            case "playtime" -> RequirementType.PLAYTIME;
            default -> null;
        };
        if (type == null) return null;
        List<RequirementProgress> values = render.progress(player, next.get());
        Optional<RequirementProgress> progress = values.stream().filter(value -> value.definition().type() == type).findFirst();
        if (progress.isEmpty()) return "0";
        return switch (parts[2]) {
            case "current" -> NumberFormats.number(progress.get().current());
            case "required" -> NumberFormats.number(progress.get().required());
            case "missing" -> NumberFormats.number(progress.get().missing());
            default -> null;
        };
    }

    private String formatted(String value, String format) {
        var component = configs.formatter().component(value);
        return switch (format) {
            case "PLAIN" -> configs.formatter().plain(component);
            case "LEGACY" -> configs.formatter().legacy(component);
            default -> configs.formatter().miniMessage(component);
        };
    }
}
