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

    public RepairReviewerAgent(GitTools gitTools, ReviewPolicy reviewPolicy) {
        this.gitTools = gitTools;
        this.reviewPolicy = reviewPolicy;
    }

    public ReviewDecision review(RepairExecutionResult executionResult) {
        List<String> changedFiles = gitTools.changedTargetFiles();
        if (!executionResult.testResult().success()) {
            return new ReviewDecision(
                    ReviewStatus.REVISE,
                    "Target-service tests are still failing.",
                    "Do not commit a patch without a passing test suite.",
                    changedFiles);
        }

        if (changedFiles.isEmpty()) {
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
                "Low risk: a defensive parameter validation was added before division.",
                changedFiles);
    }
}
