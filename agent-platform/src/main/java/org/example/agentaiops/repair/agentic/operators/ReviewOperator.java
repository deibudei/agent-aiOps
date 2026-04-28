package org.example.agentaiops.repair.agentic.operators;

import java.util.Map;
import org.example.agentaiops.repair.agent.RepairReviewerAgent;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Reviews patch, diff, and test result before external actions. */
public final class ReviewOperator {

    private final AgenticRepairState state;
    private final RepairReviewerAgent reviewerAgent;
    private final RepairEventHub eventHub;

    public ReviewOperator(AgenticRepairState state, RepairReviewerAgent reviewerAgent, RepairEventHub eventHub) {
        this.state = state;
        this.reviewerAgent = reviewerAgent;
        this.eventHub = eventHub;
    }

    public ReviewDecision reviewRepair() {
        state.execution = state.execution();
        eventHub.publish(state.sessionId, RepairStage.REVIEWING, "Reviewing diff and test result");
        state.reviewDecision = reviewerAgent.review(state.execution);
        state.step("ReviewAgent", "execution", state.reviewDecision.reason(),
                state.reviewDecision.status() == ReviewStatus.PASS);
        eventHub.publish(state.sessionId, RepairStage.REVIEWING, state.reviewDecision.reason(),
                Map.of("review", state.reviewDecision));
        return state.reviewDecision;
    }
}
