package org.example.agentaiops.repair.model;

/** One line in a unified diff hunk. */
public record RepairDiffLine(
        String type,
        Integer oldLineNumber,
        Integer newLineNumber,
        String content) {
}
