package com.zpkdxgames.plexonranks.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RankRegistry {
    private final Map<String, Rank> byId;
    private final List<Rank> ordered;
    private final Rank defaultRank;

    public RankRegistry(Collection<Rank> ranks) {
        List<Rank> enabled = ranks.stream()
                .filter(Rank::enabled)
                .sorted(Comparator.comparingInt(Rank::order))
                .toList();
        if (enabled.isEmpty()) {
            throw new IllegalArgumentException("At least one enabled rank is required");
        }
        Map<String, Rank> index = new LinkedHashMap<>();
        for (Rank rank : ranks) {
            Rank previous = index.put(rank.id().toLowerCase(Locale.ROOT), rank);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate rank ID: " + rank.id());
            }
        }
        this.byId = Map.copyOf(index);
        this.ordered = List.copyOf(enabled);
        this.defaultRank = enabled.stream().filter(Rank::defaultRank).findFirst().orElse(enabled.getFirst());
    }

    public List<Rank> ordered() {
        return ordered;
    }

    public Collection<Rank> all() {
        return byId.values();
    }

    public Rank defaultRank() {
        return defaultRank;
    }

    public Optional<Rank> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Rank> find(String input) {
        Optional<Rank> direct = byId(input).filter(Rank::enabled);
        if (direct.isPresent()) {
            return direct;
        }
        String normalized = input == null ? "" : input.trim();
        return byId.values().stream()
                .filter(Rank::enabled)
                .filter(rank -> rank.display().shortName().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public Optional<Rank> next(Rank current) {
        int index = ordered.indexOf(current);
        if (index < 0) {
            return Optional.of(defaultRank);
        }
        for (int i = index + 1; i < ordered.size(); i++) {
            Rank candidate = ordered.get(i);
            if (candidate.bypassPermission().isBlank()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<Rank> nextAccessible(Rank current, java.util.function.Predicate<String> permissionCheck) {
        int index = ordered.indexOf(current);
        if (index < 0) {
            return Optional.of(defaultRank);
        }
        for (int i = index + 1; i < ordered.size(); i++) {
            Rank candidate = ordered.get(i);
            if (candidate.bypassPermission().isBlank() || permissionCheck.test(candidate.bypassPermission())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<Rank> shift(Rank current, int amount) {
        int index = ordered.indexOf(current);
        if (index < 0) {
            return Optional.of(defaultRank);
        }
        int target = Math.max(0, Math.min(ordered.size() - 1, index + amount));
        return Optional.of(ordered.get(target));
    }

    public int position(Rank rank) {
        return ordered.indexOf(rank);
    }

    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }
}
