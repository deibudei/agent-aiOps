package org.example.agentaiops.repair.agentic.operators;

import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.RepairOutcome;
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

    public String sendNotification() {
        if (state.outcome == RepairOutcome.ERROR) {
            state.notificationResult = new NotificationResult(true, "Feishu skipped for ERROR outcome");
            state.step("FeishuTools", state.sessionId, state.notificationResult.message(), true);
            return state.notificationResult.message();
        }
        eventHub.publish(state.sessionId, RepairStage.NOTIFIED, "Sending Feishu notification");
        String repairTarget = state.plan == null || state.plan.repairTarget() == null
                ? "target-service repair"
                : state.plan.repairTarget();
        String rootCause = state.plan == null || state.plan.rootCauseHypothesis() == null
                ? ""
                : state.plan.rootCauseHypothesis();
        String reviewReason = state.reviewDecision == null || state.reviewDecision.reason() == null
                ? ""
                : state.reviewDecision.reason();
        state.notificationResult = feishuTools.sendRepairCard(
                state.sessionId,
                state.outcome,
                state.outcomeReason,
                repairTarget,
                rootCause,
                reviewReason,
                state.pullRequestResult,
                state.timing());
        state.step("FeishuTools", state.sessionId, state.notificationResult.message(),
                state.notificationResult.success());
        eventHub.publish(state.sessionId, RepairStage.NOTIFIED, state.notificationResult.message(),
                Map.of("notification", state.notificationResult));
        return state.notificationResult.message();
    }
}
