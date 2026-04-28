package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticOutputFormatter;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
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
            outputKey = "pullRequestMessage")
    public String createPullRequest() {
        eventHub.publish(state.sessionId, RepairStage.PR_CREATED, "Creating GitHub pull request");
        state.pullRequestResult = gitHubTools.createPullRequest(
                state.gitCommitResult.branchName(),
                "fix: auto repair target-service validation",
                AgenticOutputFormatter.buildPrBody(state.sessionId, state.plan, state.reviewDecision));
        state.step("GitHubTools", state.gitCommitResult.branchName(), state.pullRequestResult.message(),
                state.pullRequestResult.success());
        eventHub.publish(state.sessionId, RepairStage.PR_CREATED, state.pullRequestResult.message(),
                Map.of("pullRequest", state.pullRequestResult));
        return state.pullRequestResult.message();
    }
}
