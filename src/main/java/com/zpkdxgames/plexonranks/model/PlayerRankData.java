package com.zpkdxgames.plexonranks.model;

import java.time.Instant;
import java.util.UUID;

public record PlayerRankData(UUID uuid, String rankId, Instant updatedAt, Instant firstJoinedAt) {
}

