package org.example.agentaiops.repair.model;

import java.util.List;

public record ReviewDecision(
        ReviewStatus status,
        String reason,
        String risk,
        List<String> changedFiles) {
}
