package com.zpkdxgames.plexonranks.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Immutable, fully validated progression graph. */
public final class RankRegistry {
    private final Map<String, Rank> byId;
    private final List<Rank> ordered;
    private final List<Rank> visible;
    private final Map<String, Integer> orderedPositions;
    private final Map<String, Rank> nextById;
    private final Rank defaultRank;
    private final Rank terminalRank;

    public RankRegistry(Collection<Rank> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            throw new IllegalArgumentException("At least one rank is required");
        }

        Map<String, Rank> index = new LinkedHashMap<>();
        for (Rank rank : ranks) {
            if (rank == null || rank.id().isBlank()) {
                throw new IllegalArgumentException("Rank IDs cannot be blank");
            }
            String key = key(rank.id());
            Rank previous = index.put(key, rank);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate rank ID: " + rank.id());
            }
        }

        List<Rank> enabled = ranks.stream()
                .filter(Rank::enabled)
                .sorted(Comparator.comparingInt(Rank::order))
                .toList();
        if (enabled.isEmpty()) {
            throw new IllegalArgumentException("At least one enabled rank is required");
        }

        Set<Integer> orders = new HashSet<>();
        for (Rank rank : enabled) {
            if (!orders.add(rank.order())) {
                throw new IllegalArgumentException("Duplicate enabled rank order: " + rank.order());
            }
        }

        List<Rank> defaults = enabled.stream().filter(Rank::defaultRank).toList();
        if (defaults.size() != 1) {
            throw new IllegalArgumentException("Exactly one enabled default/root rank is required; found " + defaults.size());
        }
        this.defaultRank = defaults.getFirst();
        if (defaultRank != enabled.getFirst()) {
            throw new IllegalArgumentException("Default/root rank must be the lowest enabled progression order");
        }

        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int i = 0; i < enabled.size(); i++) {
            positions.put(key(enabled.get(i).id()), i);
        }

        Map<String, Rank> edges = new LinkedHashMap<>();
        for (int i = 0; i < enabled.size(); i++) {
            Rank rank = enabled.get(i);
            Rank target = null;
            if (!rank.nextRankId().isBlank()) {
                target = index.get(key(rank.nextRankId()));
                if (target == null || !target.enabled()) {
                    throw new IllegalArgumentException("Rank '" + rank.id() + "' points to missing/disabled next rank '"
                            + rank.nextRankId() + "'");
                }
            } else if (i + 1 < enabled.size()) {
                target = enabled.get(i + 1);
            }
            if (target != null) {
                if (target.id().equalsIgnoreCase(rank.id())) {
                    throw new IllegalArgumentException("Rank '" + rank.id() + "' cannot point to itself");
                }
                if (target.order() <= rank.order()) {
                    throw new IllegalArgumentException("Rank '" + rank.id() + "' must progress to a strictly higher order; target '"
                            + target.id() + "' has order " + target.order());
                }
                edges.put(key(rank.id()), target);
            }
        }

        validateNoCycles(defaultRank, edges);
        validateReachability(enabled, defaultRank, edges);
        List<Rank> terminals = enabled.stream().filter(rank -> !edges.containsKey(key(rank.id()))).toList();
        if (terminals.size() != 1) {
            throw new IllegalArgumentException("Exactly one terminal rank is required; found " + terminals.size());
        }

        this.byId = Map.copyOf(index);
        this.ordered = List.copyOf(enabled);
        this.visible = enabled.stream().filter(Rank::visible).toList();
        this.orderedPositions = Map.copyOf(positions);
        this.nextById = Map.copyOf(edges);
        this.terminalRank = terminals.getFirst();
    }

    public List<Rank> ordered() {
        return ordered;
    }

    public List<Rank> visible() {
        return visible;
    }

    public Collection<Rank> all() {
        return byId.values();
    }

    public Rank defaultRank() {
        return defaultRank;
    }

    public Rank terminalRank() {
        return terminalRank;
    }

    public Optional<Rank> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(key(id)));
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

    /** Returns the immediate resolved progression edge, including gated ranks. */
    public Optional<Rank> next(Rank current) {
        if (current == null) {
            return Optional.of(defaultRank);
        }
        return Optional.ofNullable(nextById.get(key(current.id())));
    }

    /** Follows progression edges until the next rank accessible to the player is found. */
    public Optional<Rank> nextAccessible(Rank current, Predicate<String> permissionCheck) {
        Optional<Rank> cursor = next(current);
        Set<String> visited = new HashSet<>();
        while (cursor.isPresent()) {
            Rank candidate = cursor.get();
            if (!visited.add(key(candidate.id()))) {
                throw new IllegalStateException("Progression cycle escaped startup validation at " + candidate.id());
            }
            if (candidate.bypassPermission().isBlank() || permissionCheck.test(candidate.bypassPermission())) {
                return Optional.of(candidate);
            }
            cursor = next(candidate);
        }
        return Optional.empty();
    }

    public Optional<Rank> shift(Rank current, int amount) {
        int index = position(current);
        if (index < 0) {
            return Optional.of(defaultRank);
        }
        int target = Math.max(0, Math.min(ordered.size() - 1, index + amount));
        return Optional.of(ordered.get(target));
    }

    public int position(Rank rank) {
        if (rank == null) {
            return -1;
        }
        return orderedPositions.getOrDefault(key(rank.id()), -1);
    }

    public List<Rank> pathFromRoot() {
        List<Rank> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Rank cursor = defaultRank;
        while (cursor != null && visited.add(key(cursor.id()))) {
            path.add(cursor);
            cursor = nextById.get(key(cursor.id()));
        }
        return List.copyOf(path);
    }

    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    private static void validateNoCycles(Rank root, Map<String, Rank> edges) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        dfs(root, edges, visiting, visited);
    }

    private static void dfs(Rank rank, Map<String, Rank> edges, Set<String> visiting, Set<String> visited) {
        String id = key(rank.id());
        if (visited.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Progression cycle detected at rank '" + rank.id() + "'");
        }
        Rank next = edges.get(id);
        if (next != null) {
            dfs(next, edges, visiting, visited);
        }
        visiting.remove(id);
        visited.add(id);
    }

    private static void validateReachability(List<Rank> enabled, Rank root, Map<String, Rank> edges) {
        Set<String> reachable = new HashSet<>();
        Rank cursor = root;
        while (cursor != null && reachable.add(key(cursor.id()))) {
            cursor = edges.get(key(cursor.id()));
        }
        List<String> missing = enabled.stream()
                .filter(rank -> !reachable.contains(key(rank.id())))
                .map(Rank::id)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Enabled ranks are unreachable from default/root: " + String.join(", ", missing));
        }
    }

    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
