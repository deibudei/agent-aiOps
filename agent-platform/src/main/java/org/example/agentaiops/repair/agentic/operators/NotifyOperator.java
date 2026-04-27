package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.FeishuTools;

/** Sends Feishu notification when enabled by configuration. */
public final class NotifyOperator {

    private final AgenticRepairState state;
    private final FeishuTools feishuTools;
    private final RepairEventHub eventHub;

    public NotifyOperator(AgenticRepairState state, FeishuTools feishuTools, RepairEventHub eventHub) {
        this.state = state;
        this.feishuTools = feishuTools;
        this.eventHub = eventHub;
    }

    @Agent(name = "sendNotification", description = "Send Feishu notification after PR step",
            outputKey = "notificationResult")
    public NotificationResult sendNotification(@V("pullRequestResult") PullRequestResult pullRequestResult) {
        eventHub.publish(state.sessionId, RepairStage.NOTIFIED, "Sending Feishu notification");
        state.notificationResult = feishuTools.sendRepairCard(
                state.sessionId,
                pullRequestResult,
                state.plan.rootCauseHypothesis(),
                state.reviewDecision.reason());
        state.step("FeishuTools", state.sessionId, state.notificationResult.message(),
                state.notificationResult.success());
        eventHub.publish(state.sessionId, RepairStage.NOTIFIED, state.notificationResult.message(),
                Map.of("notification", state.notificationResult));
        return state.notificationResult;
    }
}
