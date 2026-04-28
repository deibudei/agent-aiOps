package org.example.agentaiops.repair.model;

/** Aggregates model identity and token usage for one Agentic repair step. */
public record RepairModelUsage(
        String stepName,
        String role,
        String configuredModel,
        String responseModel,
        int callCount,
        Integer inputTokenCount,
        Integer outputTokenCount,
        Integer totalTokenCount) {
}
