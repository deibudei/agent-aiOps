package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticFallbacks;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.GitHubTools;

/** Creates a GitHub pull request when enabled by configuration. */
public final class PullRequestOperator {

    private final AgenticRepairState state;
    private final GitHubTools gitHubTools;
    private final RepairEventHub eventHub;

    public PullRequestOperator(AgenticRepairState state, GitHubTools gitHubTools, RepairEventHub eventHub) {
        this.state = state;
        this.gitHubTools = gitHubTools;
        this.eventHub = eventHub;
    }

    @Agent(name = "createPullRequest", description = "Create GitHub PR after commit step",
            outputKey = "pullRequestResult")
    public PullRequestResult createPullRequest(@V("gitCommitResult") GitCommitResult gitCommitResult) {
        eventHub.publish(state.sessionId, RepairStage.PR_CREATED, "Creating GitHub pull request");
        state.pullRequestResult = gitHubTools.createPullRequest(
                gitCommitResult.branchName(),
                "fix: auto repair target-service validation",
                AgenticFallbacks.buildPrBody(state.sessionId, state.plan, state.reviewDecision));
        state.step("GitHubTools", gitCommitResult.branchName(), state.pullRequestResult.message(),
                state.pullRequestResult.success());
        eventHub.publish(state.sessionId, RepairStage.PR_CREATED, state.pullRequestResult.message(),
                Map.of("pullRequest", state.pullRequestResult));
        return state.pullRequestResult;
    }
}
