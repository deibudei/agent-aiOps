package org.example.agentaiops.repair.agentic.operators;

import java.util.Map;
import org.example.agentaiops.repair.agent.RepairReflectionAgent;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Generates the post-repair reflection. */
public final class ReflectOperator {

    private final AgenticRepairState state;
    private final RepairReflectionAgent reflectionAgent;
    private final RepairEventHub eventHub;

    public ReflectOperator(
            AgenticRepairState state, RepairReflectionAgent reflectionAgent, RepairEventHub eventHub) {
        this.state = state;
        this.reflectionAgent = reflectionAgent;
        this.eventHub = eventHub;
    }

    public String reflectRepair() {
        eventHub.publish(state.sessionId, RepairStage.REFLECTING, "Generating repair reflection");
        state.reflection = reflectionAgent.reflect(
                state.evidence, state.plan, state.execution(), state.reviewDecision);
        state.step("ReflectionAgent", state.sessionId, state.reflection.fixStrategy(), true);
        eventHub.publish(state.sessionId, RepairStage.REFLECTING, "Repair reflection generated",
                Map.of("reflection", state.reflection));
        return state.reflection.fixStrategy();
    }
}
