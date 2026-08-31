package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.ValidationIssue;

import java.util.List;

public record ReloadResult(boolean success, boolean restartRequired, List<ValidationIssue> issues) {
    public ReloadResult {
        issues = List.copyOf(issues);
    }
}

