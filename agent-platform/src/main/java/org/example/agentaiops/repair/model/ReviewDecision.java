package org.example.agentaiops.repair.model;

import java.util.List;

/** Describes whether a generated repair is safe to publish. */
public record ReviewDecision(
        ReviewStatus status,
        String reason,
        String risk,
        List<String> changedFiles) {
}
