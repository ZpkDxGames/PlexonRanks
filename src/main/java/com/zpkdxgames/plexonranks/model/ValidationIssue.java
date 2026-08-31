package com.zpkdxgames.plexonranks.model;

public record ValidationIssue(Severity severity, String source, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }
}

