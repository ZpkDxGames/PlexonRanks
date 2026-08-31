package com.zpkdxgames.plexonranks.model;

import java.util.List;

public record RankDisplay(String name, String shortName, String tag, List<String> description) {
    public RankDisplay {
        description = List.copyOf(description);
    }
}

