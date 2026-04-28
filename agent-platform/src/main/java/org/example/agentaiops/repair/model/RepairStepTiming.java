package org.example.agentaiops.repair.model;

import java.time.Instant;

/** Captures timing for one visible Agentic repair step. */
public record RepairStepTiming(
        String stepName,
        Instant startedAt,
        Instant completedAt,
        long durationMillis,
        boolean success,
        String summary,
        String modelRole,
        String modelName,
        Integer inputTokenCount,
        Integer outputTokenCount,
        Integer totalTokenCount) {

    public RepairStepTiming(
            String stepName,
            Instant startedAt,
            Instant completedAt,
            long durationMillis,
            boolean success,
            String summary) {
        this(stepName, startedAt, completedAt, durationMillis, success, summary, null, null, null, null, null);
    }
}
