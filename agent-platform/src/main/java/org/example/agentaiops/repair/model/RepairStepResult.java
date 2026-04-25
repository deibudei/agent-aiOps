package org.example.agentaiops.repair.model;

public record RepairStepResult(
        String toolName,
        String inputSummary,
        String outputSummary,
        boolean success) {
}
