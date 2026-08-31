package com.zpkdxgames.plexonranks.requirement;

public record Consumption(Runnable rollback) {
    public static Consumption none() {
        return new Consumption(() -> { });
    }
}

