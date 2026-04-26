package org.example.agentaiops.repair.model;

/** Records one tool or Agent step for event streaming and repair records. */
public record RepairStepResult(
        String toolName,
        String inputSummary,
        String outputSummary,
        boolean success) {
}
