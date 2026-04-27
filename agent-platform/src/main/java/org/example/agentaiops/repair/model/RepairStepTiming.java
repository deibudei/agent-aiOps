package org.example.agentaiops.repair.model;

import java.time.Instant;

/** Captures timing for one visible Agentic repair step. */
public record RepairStepTiming(
        String stepName,
        Instant startedAt,
        Instant completedAt,
        long durationMillis,
        boolean success,
        String summary) {
}
