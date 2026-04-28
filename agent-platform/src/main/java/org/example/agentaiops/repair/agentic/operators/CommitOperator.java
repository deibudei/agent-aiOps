package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ReviewDecision;
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

    @Agent(name = "commitRepair", description = "Create repair branch and commit when review passes",
            outputKey = "gitCommitMessage")
    public String commitRepair(@V("reviewDecision") ReviewDecision reviewDecision) {
        if (reviewDecision.status() != ReviewStatus.PASS) {
            state.gitCommitResult = new GitCommitResult(false, "", "", "Review did not pass");
            return state.gitCommitResult.message();
        }
        eventHub.publish(state.sessionId, RepairStage.COMMITTING, "Creating repair branch and commit");
        state.gitCommitResult = gitTools.commitAndPush(state.sessionId);
        state.step("GitTools", state.sessionId, state.gitCommitResult.message(), state.gitCommitResult.success());
        eventHub.publish(state.sessionId, RepairStage.COMMITTING, state.gitCommitResult.message(),
                Map.of("git", state.gitCommitResult));
        return state.gitCommitResult.message();
    }
}
