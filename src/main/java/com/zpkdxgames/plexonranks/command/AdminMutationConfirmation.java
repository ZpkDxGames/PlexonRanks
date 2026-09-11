package com.zpkdxgames.plexonranks.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory guard for destructive administrative rank mutations.
 * A confirmation is valid only for the same actor, target, action/value,
 * authoritative rank and immutable configuration generation that were staged.
 */
final class AdminMutationConfirmation {
    private final long windowMillis;
    private final Map<String, Pending> pendingByActor = new HashMap<>();

    AdminMutationConfirmation(long windowMillis) {
        if (windowMillis <= 0) throw new IllegalArgumentException("windowMillis must be positive");
        this.windowMillis = windowMillis;
    }

    synchronized void stage(String actorKey, UUID playerId, String action, String expectedRankId,
                            Object generation, long nowMillis) {
        pendingByActor.put(actorKey, new Pending(playerId, action, expectedRankId, generation,
                Math.addExact(nowMillis, windowMillis)));
    }

    synchronized Verification verify(String actorKey, UUID playerId, String action, String currentRankId,
                                     Object generation, long nowMillis) {
        Pending pending = pendingByActor.get(actorKey);
        if (pending == null) return Verification.MISSING;
        if (pending.expiresAt() < nowMillis) {
            pendingByActor.remove(actorKey);
            return Verification.EXPIRED;
        }
        if (!pending.playerId().equals(playerId) || !pending.action().equals(action)) {
            return Verification.MISMATCH;
        }
        if (!pending.expectedRankId().equalsIgnoreCase(currentRankId) || pending.generation() != generation) {
            pendingByActor.remove(actorKey);
            return Verification.STALE;
        }
        pendingByActor.remove(actorKey);
        return Verification.CONFIRMED;
    }

    synchronized int pendingCount() {
        return pendingByActor.size();
    }

    enum Verification {
        CONFIRMED,
        MISSING,
        EXPIRED,
        MISMATCH,
        STALE
    }

    private record Pending(UUID playerId, String action, String expectedRankId, Object generation, long expiresAt) {
        private Pending {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(expectedRankId, "expectedRankId");
            Objects.requireNonNull(generation, "generation");
        }
    }
}
