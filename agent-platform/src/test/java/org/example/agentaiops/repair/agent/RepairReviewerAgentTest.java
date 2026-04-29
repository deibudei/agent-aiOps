package org.example.agentaiops.repair.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.example.agentaiops.repair.extension.ReviewPolicy;
import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.tool.GitTools;
import org.junit.jupiter.api.Test;

class RepairReviewerAgentTest {

    @Test
    void passesCleanDemoRestorationWhenPatchAppliedAndTestsPass() {
        GitTools gitTools = mock(GitTools.class);
        ReviewPolicy reviewPolicy = mock(ReviewPolicy.class);
        when(gitTools.changedTargetFiles()).thenReturn(List.of());
        RepairReviewerAgent reviewerAgent = new RepairReviewerAgent(gitTools, reviewPolicy);

        var decision = reviewerAgent.review(executionResult("return totalCents / quantity;", "return guarded;"));

        assertThat(decision.status()).isEqualTo(ReviewStatus.PASS);
        assertThat(decision.reason()).contains("clean baseline");
    }

    @Test
    void rejectsEmptyDiffWhenPatchOperationIsNoOp() {
        GitTools gitTools = mock(GitTools.class);
        ReviewPolicy reviewPolicy = mock(ReviewPolicy.class);
        when(gitTools.changedTargetFiles()).thenReturn(List.of());
        RepairReviewerAgent reviewerAgent = new RepairReviewerAgent(gitTools, reviewPolicy);

        var decision = reviewerAgent.review(executionResult("same", "same"));

        assertThat(decision.status()).isEqualTo(ReviewStatus.REJECT);
        assertThat(decision.reason()).contains("No target-service diff");
    }

    @Test
    void rejectsWhenTestsDidNotRun() {
        GitTools gitTools = mock(GitTools.class);
        ReviewPolicy reviewPolicy = mock(ReviewPolicy.class);
        when(gitTools.changedTargetFiles()).thenReturn(List.of());
        RepairReviewerAgent reviewerAgent = new RepairReviewerAgent(gitTools, reviewPolicy);

        PatchResult patchResult = new PatchResult(
                true,
                "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                "Patch applied");
        PatchApplicationResult applicationResult =
                new PatchApplicationResult(true, List.of(patchResult), "Patch proposal applied");

        var decision = reviewerAgent.review(new RepairExecutionResult(
                List.of(),
                null,
                patchResult,
                null,
                applicationResult));

        assertThat(decision.status()).isEqualTo(ReviewStatus.REJECT);
        assertThat(decision.reason()).contains("tests did not run");
    }

    private RepairExecutionResult executionResult(String oldText, String newText) {
        PatchProposal proposal = new PatchProposal(
                "target",
                "root cause",
                List.of(new PatchOperation(
                        "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                        oldText,
                        newText,
                        "fix")),
                List.of("mvn -pl target-service test"),
                true,
                "");
        PatchResult patchResult = new PatchResult(
                true,
                "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                "Patch applied");
        PatchApplicationResult applicationResult =
                new PatchApplicationResult(true, List.of(patchResult), "Patch proposal applied");
        return new RepairExecutionResult(
                List.of(),
                new TestExecutionResult(0, "ok", "", 100, false),
                patchResult,
                proposal,
                applicationResult);
    }
}
