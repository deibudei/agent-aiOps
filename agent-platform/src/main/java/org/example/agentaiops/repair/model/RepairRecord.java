package org.example.agentaiops.repair.model;

import java.time.Instant;
import java.util.List;

public record RepairRecord(
        int recordVersion,
        String sessionId,
        Instant startedAt,
        Instant completedAt,
        String tracebackSummary,
        RepairPlan plan,
        List<RepairStepResult> stepResults,
        String diffSummary,
        TestExecutionResult testResult,
        ReviewDecision reviewDecision,
        GitCommitResult gitCommitResult,
        PullRequestResult pullRequestResult,
        NotificationResult notificationResult,
        RepairReflection reflection) {
}
