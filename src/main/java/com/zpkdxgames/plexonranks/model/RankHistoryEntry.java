package com.zpkdxgames.plexonranks.model;

import java.time.Instant;
import java.util.UUID;

public record RankHistoryEntry(
        long id,
        UUID playerId,
        String fromRank,
        String toRank,
        String cause,
        String transactionId,
        String status,
        Instant createdAt,
        String detail
) {
    public RankHistoryEntry {
        fromRank = fromRank == null ? "" : fromRank;
        toRank = toRank == null ? "" : toRank;
        cause = cause == null ? "UNKNOWN" : cause;
        transactionId = transactionId == null ? "" : transactionId;
        status = status == null ? "UNKNOWN" : status;
        detail = detail == null ? "" : detail;
    }
}
