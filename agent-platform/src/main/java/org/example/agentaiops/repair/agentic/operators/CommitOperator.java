package org.example.agentaiops.repair.agentic.operators;

import java.util.LinkedHashMap;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.GitTools;

/** Commits and pushes only after review passes and Git automation is enabled. */
public final class CommitOperator {

    private final AgenticRepairState state;
    private final GitTools gitTools;
    private final RepairEventHub eventHub;

    public CommitOperator(AgenticRepairState state, GitTools gitTools, RepairEventHub eventHub) {
        this.state = state;
        this.gitTools = gitTools;
        this.eventHub = eventHub;
    }

    public String commitRepair() {
        if (state.reviewDecision == null || state.reviewDecision.status() != ReviewStatus.PASS) {
            state.gitCommitResult = new GitCommitResult(false, "", "", "Review did not pass");
            return state.gitCommitResult.message();
        }
        String repairTarget = state.plan == null || state.plan.repairTarget() == null
                ? "target-service repair"
                : state.plan.repairTarget();
        String branchName = "repair/" + state.sessionId.replaceAll("[^A-Za-z0-9._-]", "-");
        eventHub.publish(state.sessionId, RepairStage.COMMITTING, "GitCommit started: " + branchName,
                toolDetails(
                        "tool_started",
                        "GitCommit",
                        branchName,
                        "running",
                        true,
                        "Creating repair branch, committing target-service changes, and pushing",
                        null));
        state.gitCommitResult = gitTools.commitAndPush(state.sessionId, repairTarget);
        state.step("GitTools", state.sessionId, state.gitCommitResult.message(), state.gitCommitResult.success());
        eventHub.publish(state.sessionId, RepairStage.COMMITTING, state.gitCommitResult.message(),
                toolDetails(
                        "tool_completed",
                        "GitCommit",
                        state.gitCommitResult.branchName().isBlank() ? branchName : state.gitCommitResult.branchName(),
                        state.gitCommitResult.success() ? "completed" : "failed",
                        state.gitCommitResult.success(),
                        state.gitCommitResult.message(),
                        state.gitCommitResult));
        return state.gitCommitResult.message();
    }

    private Map<String, Object> toolDetails(
            String eventType,
            String toolName,
            String target,
            String status,
            boolean success,
            String summary,
            GitCommitResult result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", eventType);
        details.put("toolName", toolName);
        details.put("target", target);
        details.put("status", status);
        details.put("success", success);
        details.put("summary", summary);
        if (result != null) {
            details.put("branchName", result.branchName());
            details.put("commitMessage", result.commitMessage());
            details.put("git", result);
        }
        return details;
    }
}
