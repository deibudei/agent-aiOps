package org.example.agentaiops.repair.agentic.operators;

import java.util.Map;
import org.example.agentaiops.repair.agentic.agents.AgenticReflectionAgent;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Generates the post-repair reflection. */
public final class ReflectOperator {

    private final AgenticRepairState state;
    private final AgenticReflectionAgent reflectionAgent;
    private final RepairEventHub eventHub;

    public ReflectOperator(
            AgenticRepairState state, AgenticReflectionAgent reflectionAgent, RepairEventHub eventHub) {
        this.state = state;
        this.reflectionAgent = reflectionAgent;
        this.eventHub = eventHub;
    }

    public String reflectRepair() {
        eventHub.publish(state.sessionId, RepairStage.REFLECTING, "Generating repair reflection");
        state.reflection = generateWithOneRetry();
        state.step("ReflectionAgent", state.sessionId, state.reflection.fixStrategy(), true);
        eventHub.publish(state.sessionId, RepairStage.REFLECTING, "Repair reflection generated",
                Map.of("reflection", state.reflection));
        return state.reflection.fixStrategy();
    }

    private RepairReflection generateWithOneRetry() {
        RuntimeException firstFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                RepairReflection reflection = reflectionAgent.reflectRepair(
                        state.evidence,
                        state.plan,
                        state.execution(),
                        state.reviewDecision,
                        state.outcome,
                        state.outcomeReason);
                validate(reflection);
                return reflection;
            } catch (RuntimeException e) {
                firstFailure = e;
                eventHub.publish(state.sessionId, RepairStage.REFLECTING,
                        "Reflection output invalid on attempt " + attempt + ": " + e.getMessage());
            }
        }
        throw firstFailure;
    }

    private void validate(RepairReflection reflection) {
        if (reflection == null) {
            throw new IllegalStateException("Agentic reflection output was not a RepairReflection");
        }
        requireText(reflection.rootCause(), "rootCause");
        requireText(reflection.keyEvidence(), "keyEvidence");
        requireText(reflection.fixStrategy(), "fixStrategy");
        requireText(reflection.testCoverage(), "testCoverage");
        requireText(reflection.lessonsLearned(), "lessonsLearned");
        if (reflection.futureHints() == null || reflection.futureHints().isEmpty()) {
            throw new IllegalStateException("RepairReflection futureHints must not be empty");
        }
        for (String hint : reflection.futureHints()) {
            requireText(hint, "futureHints");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("RepairReflection " + fieldName + " is required");
        }
    }
}
