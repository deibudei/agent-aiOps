package org.example.agentaiops.demo;

import java.util.List;

/** Read-only PR-safe demo readiness summary for the frontend dashboard. */
public record DemoPrScenarioReadiness(
        String faultType,
        String expectedBaseBranch,
        String configuredBaseBranch,
        boolean baseBranchMatches,
        boolean llmEnabled,
        boolean gitEnabled,
        boolean githubEnabled,
        boolean feishuEnabled,
        String worktreeRoot,
        boolean ready,
        List<String> warnings) {
}
