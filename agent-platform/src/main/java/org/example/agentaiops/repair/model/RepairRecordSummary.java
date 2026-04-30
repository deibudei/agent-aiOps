package org.example.agentaiops.repair.model;

import java.time.Instant;

/** Compact repair-record summary for experiment index views. */
public record RepairRecordSummary(
        String sessionId,
        RepairOutcome outcome,
        String outcomeReason,
        Instant startedAt,
        Instant completedAt,
        Long durationMillis,
        int patchAttempts,
        Long totalTokens,
        Boolean testSuccess,
        Integer testExitCode,
        String prUrl,
        Boolean notificationSuccess,
        String recordPath) {
}
