package org.example.agentaiops.repair.model;

import java.time.Instant;
import java.util.List;

/** Captures one full repair run for JSON storage and Markdown reporting. */
public record RepairRecord(
        int recordVersion,
        String sessionId,
        Instant startedAt,
        Instant completedAt,
        EvidenceBundle evidenceBundle,
        String tracebackSummary,
        RepairPlan plan,
        List<RepairStepResult> stepResults,
        PatchProposal patchProposal,
        PatchApplicationResult patchApplicationResult,
        String diffSummary,
        TestExecutionResult testResult,
        ReviewDecision reviewDecision,
        GitCommitResult gitCommitResult,
        PullRequestResult pullRequestResult,
        NotificationResult notificationResult,
        RepairReflection reflection) {
}
