package com.zpkdxgames.plexonranks.integration;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.config.ConfigSnapshot;
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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PlexonRanksExpansion extends PlaceholderExpansion {
    private static final long DEFAULT_CACHE_TICKS = 4L;
    private static final long MAX_CACHE_TICKS = 20L;
    private static final long RECURSION_WARNING_INTERVAL_NANOS = 30_000_000_000L;

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final RenderService render;
    private final ConcurrentMap<UUID, DisplaySnapshot> displaySnapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<FormatKey, String> formattedCache = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<String>> activeRequests = ThreadLocal.withInitial(HashSet::new);
    private final AtomicLong lastRecursionWarning = new AtomicLong();

    private volatile ConfigSnapshot cacheGeneration;
    private volatile long snapshotTtlNanos = DEFAULT_CACHE_TICKS * 50_000_000L;

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

        String key = params.toLowerCase(Locale.ROOT);
        String recursionKey = player.getUniqueId() + ":" + key;
        Set<String> requests = activeRequests.get();
        if (!requests.add(recursionKey)) {
            warnRecursive(player, key);
            return "";
        }

        try {
            ConfigSnapshot generation = configs.current();
            refreshGeneration(generation);
            Rank current = ranks.current(player.getUniqueId()).orElse(generation.registry().defaultRank());

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
                case "next_id" -> displaySnapshot(player, current, generation).next().map(Rank::id).orElse("");
                case "next_name" -> displaySnapshot(player, current, generation).next()
                        .map(rank -> rank.display().name()).orElse("");
                case "next_short_name" -> displaySnapshot(player, current, generation).next()
                        .map(rank -> rank.display().shortName()).orElse("");
                case "next_name_plain" -> displaySnapshot(player, current, generation).next()
                        .map(rank -> formatted(rank.display().name(), "PLAIN")).orElse("");
                case "next_name_mm", "next_name_minimessage" -> displaySnapshot(player, current, generation).next()
                        .map(rank -> formatted(rank.display().name(), "MINIMESSAGE")).orElse("");
                case "next_name_legacy" -> displaySnapshot(player, current, generation).next()
                        .map(rank -> formatted(rank.display().name(), "LEGACY")).orElse("");
                case "is_max_rank" -> String.valueOf(displaySnapshot(player, current, generation).next().isEmpty());
                case "progress_percent" -> progressPercent(player, current, generation);
                default -> requirementPlaceholder(player, current, generation, key);
            };
        } finally {
            requests.remove(recursionKey);
            if (requests.isEmpty()) {
                activeRequests.remove();
            }
        }
    }

    private String progressPercent(Player player, Rank current, ConfigSnapshot generation) {
        DisplaySnapshot snapshot = displaySnapshot(player, current, generation);
        if (snapshot.next().isEmpty()) return "100";
        return NumberFormats.number(RequirementEngine.overallProgress(progress(player, snapshot)) * 100.0);
    }

    private String requirementPlaceholder(Player player, Rank current, ConfigSnapshot generation, String key) {
        if (!key.startsWith("requirement_")) return null;
        String[] parts = key.split("_");
        if (parts.length < 3) return null;
        RequirementType type = switch (parts[1]) {
            case "money" -> RequirementType.MONEY;
            case "xp" -> RequirementType.XP_LEVELS;
            case "playtime" -> RequirementType.PLAYTIME;
            default -> null;
        };
        if (type == null) return null;

        DisplaySnapshot snapshot = displaySnapshot(player, current, generation);
        if (snapshot.next().isEmpty()) return "0";
        Optional<RequirementProgress> value = progress(player, snapshot).stream()
                .filter(progress -> progress.definition().type() == type)
                .findFirst();
        if (value.isEmpty()) return "0";
        return switch (parts[2]) {
            case "current" -> NumberFormats.number(value.get().current());
            case "required" -> NumberFormats.number(value.get().required());
            case "missing" -> NumberFormats.number(value.get().missing());
            default -> null;
        };
    }

    private DisplaySnapshot displaySnapshot(Player player, Rank current, ConfigSnapshot generation) {
        long now = System.nanoTime();
        DisplaySnapshot cached = displaySnapshots.get(player.getUniqueId());
        if (cached != null && cached.generation() == generation
                && cached.currentRankId().equals(current.id()) && now < cached.expiresAtNanos()) {
            return cached;
        }

        Optional<Rank> next = generation.registry().nextAccessible(current, player::hasPermission);
        DisplaySnapshot replacement = new DisplaySnapshot(
                generation,
                current.id(),
                next,
                now + snapshotTtlNanos
        );
        displaySnapshots.put(player.getUniqueId(), replacement);
        return replacement;
    }

    private List<RequirementProgress> progress(Player player, DisplaySnapshot snapshot) {
        List<RequirementProgress> cached = snapshot.progress();
        if (cached != null) return cached;
        synchronized (snapshot) {
            cached = snapshot.progress();
            if (cached == null) {
                cached = snapshot.next().map(rank -> render.progress(player, rank)).orElseGet(List::of);
                snapshot.progress(cached);
            }
            return cached;
        }
    }

    private void refreshGeneration(ConfigSnapshot generation) {
        if (cacheGeneration == generation) return;
        synchronized (this) {
            if (cacheGeneration == generation) return;
            long configuredTicks = generation.config().getLong("performance.placeholder-cache-ticks", DEFAULT_CACHE_TICKS);
            long ticks = Math.max(1L, Math.min(MAX_CACHE_TICKS, configuredTicks));
            snapshotTtlNanos = ticks * 50_000_000L;
            displaySnapshots.clear();
            formattedCache.clear();
            cacheGeneration = generation;
        }
    }

    private String formatted(String value, String format) {
        return formattedCache.computeIfAbsent(new FormatKey(value, format), ignored -> {
            var component = configs.formatter().component(value);
            return switch (format) {
                case "PLAIN" -> configs.formatter().plain(component);
                case "LEGACY" -> configs.formatter().legacy(component);
                default -> configs.formatter().miniMessage(component);
            };
        });
    }

    private void warnRecursive(Player player, String key) {
        long now = System.nanoTime();
        long previous = lastRecursionWarning.get();
        if (now - previous < RECURSION_WARNING_INTERVAL_NANOS || !lastRecursionWarning.compareAndSet(previous, now)) {
            return;
        }
        plugin.getLogger().warning("Blocked recursive PlaceholderAPI evaluation for %plexonranks_" + key
                + "% (player " + player.getUniqueId() + ")");
    }

    private record FormatKey(String value, String format) {
    }

    private static final class DisplaySnapshot {
        private final ConfigSnapshot generation;
        private final String currentRankId;
        private final Optional<Rank> next;
        private final long expiresAtNanos;
        private volatile List<RequirementProgress> progress;

        private DisplaySnapshot(ConfigSnapshot generation, String currentRankId, Optional<Rank> next, long expiresAtNanos) {
            this.generation = generation;
            this.currentRankId = currentRankId;
            this.next = next;
            this.expiresAtNanos = expiresAtNanos;
        }

        private ConfigSnapshot generation() {
            return generation;
        }

        private String currentRankId() {
            return currentRankId;
        }

        private Optional<Rank> next() {
            return next;
        }

        private long expiresAtNanos() {
            return expiresAtNanos;
        }

        private List<RequirementProgress> progress() {
            return progress;
        }

        private void progress(List<RequirementProgress> progress) {
            this.progress = progress;
        }
    }
}
