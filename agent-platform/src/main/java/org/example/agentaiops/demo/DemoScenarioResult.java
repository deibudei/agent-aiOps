package org.example.agentaiops.demo;

import java.util.List;

/** Response returned by the one-click demo scenario API. */
public record DemoScenarioResult(
        String sessionId,
        String faultType,
        DemoScenarioStage stage,
        boolean success,
        String message,
        List<String> changedFiles,
        List<String> nextSteps,
        String repairStreamUrl,
        String targetServiceUrl,
        String evidenceSummary,
        String branchName,
        String worktreePath,
        String prUrl,
        Boolean notificationSuccess,
        String recordJsonPath,
        String recordMarkdownPath,
        String outcomeReason) {
}
