package org.example.agentaiops.repair.agent;

import java.util.List;
import org.example.agentaiops.repair.extension.ReviewPolicy;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.tool.GitTools;
import org.springframework.stereotype.Component;

@Component
public class RepairReviewerAgent {

    private final GitTools gitTools;
    private final ReviewPolicy reviewPolicy;

    /** Wires git diff inspection and the configured review policy. */
    public RepairReviewerAgent(GitTools gitTools, ReviewPolicy reviewPolicy) {
        this.gitTools = gitTools;
        this.reviewPolicy = reviewPolicy;
    }

    /** Blocks unsafe or unverified patches before commit, PR, or notification. */
    public ReviewDecision review(RepairExecutionResult executionResult) {
        List<String> changedFiles = gitTools.changedTargetFiles();
        if (executionResult.patchResult() == null || !executionResult.patchResult().success()) {
            return new ReviewDecision(
                    ReviewStatus.REJECT,
                    "Patch application failed or produced no valid result.",
                    "Do not commit when the Agent failed to apply a safe patch.",
                    changedFiles);
        }

        if (executionResult.patchProposal() != null
                && (executionResult.patchProposal().operations() == null
                        || executionResult.patchProposal().operations().isEmpty())) {
            return new ReviewDecision(
                    ReviewStatus.REJECT,
                    "LLM patch proposal did not contain any operation.",
                    "The Agent must produce a concrete, reviewable patch before validation.",
                    changedFiles);
        }

        if (executionResult.patchApplicationResult() != null
                && !executionResult.patchApplicationResult().success()) {
            return new ReviewDecision(
                    ReviewStatus.REJECT,
                    "LLM patch proposal failed tool-policy application.",
                    "The repair must pass path and exact-text checks before tests can be trusted.",
                    changedFiles);
        }

        if (executionResult.testResult() == null) {
            return new ReviewDecision(
                    ReviewStatus.REJECT,
                    "Target-service tests did not run.",
                    "Do not commit a patch without an explicit test result.",
                    changedFiles);
        }

        if (!executionResult.testResult().success()) {
            return new ReviewDecision(
                    ReviewStatus.REVISE,
                    "Target-service tests are still failing.",
                    "Do not commit a patch without a passing test suite.",
                    changedFiles);
        }

        if (changedFiles.isEmpty()) {
            if (isCleanDemoRestoration(executionResult)) {
                return new ReviewDecision(
                        ReviewStatus.PASS,
                        "Patch restored target-service to the current clean baseline and tests pass.",
                        "Low risk: demo fault injection was repaired back to HEAD, so no Git diff remains.",
                        changedFiles);
            }
            return new ReviewDecision(
                    ReviewStatus.REJECT,
                    "No target-service diff was produced.",
                    "No code change is available for review.",
                    changedFiles);
        }

        if (!gitTools.allChangedFilesAllowed(changedFiles)) {
            return new ReviewDecision(
                    ReviewStatus.REJECT,
                    "Diff includes files outside the strong whitelist.",
                    "The repair must not touch the Agent platform or configuration.",
                    changedFiles);
        }

        if (!reviewPolicy.allows(executionResult.testResult(), changedFiles)) {
            return new ReviewDecision(
                    ReviewStatus.REJECT,
                    "Review policy rejected this patch.",
                    "Patch did not satisfy configured safety policy.",
                    changedFiles);
        }

        return new ReviewDecision(
                ReviewStatus.PASS,
                "Patch is limited to target-service and tests pass.",
                "Low risk: the diff stays inside the allowed target-service repair surface and the regression suite passed.",
                changedFiles);
    }

    private boolean isCleanDemoRestoration(RepairExecutionResult executionResult) {
        if (executionResult.patchApplicationResult() == null
                || !executionResult.patchApplicationResult().success()
                || executionResult.patchApplicationResult().patchResults() == null
                || executionResult.patchApplicationResult().patchResults().isEmpty()
                || executionResult.patchProposal() == null
                || executionResult.patchProposal().operations() == null
                || executionResult.patchProposal().operations().isEmpty()) {
            return false;
        }
        boolean changedOperation = executionResult.patchProposal().operations().stream()
                .anyMatch(operation -> operation.oldText() != null
                        && operation.newText() != null
                        && !operation.oldText().equals(operation.newText()));
        boolean appliedPatch = executionResult.patchApplicationResult().patchResults().stream()
                .allMatch(result -> result.success()
                        && result.filePath() != null
                        && result.filePath().startsWith("target-service/src/"));
        return changedOperation && appliedPatch;
    }
}
