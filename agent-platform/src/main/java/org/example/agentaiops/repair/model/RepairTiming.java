package org.example.agentaiops.repair.model;

import java.time.Instant;
import java.util.List;

/** Captures total repair duration and per-step timing. */
public record RepairTiming(
        Instant startedAt,
        Instant completedAt,
        long durationMillis,
        List<RepairStepTiming> steps) {
}
