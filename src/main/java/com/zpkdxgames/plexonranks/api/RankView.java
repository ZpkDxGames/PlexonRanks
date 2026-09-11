package com.zpkdxgames.plexonranks.api;

public record RankView(
        String id,
        int order,
        String tier,
        String displayName,
        String shortName,
        String tag,
        boolean terminal
) {
}
