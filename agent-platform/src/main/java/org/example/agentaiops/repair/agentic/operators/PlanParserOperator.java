package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Validates the typed LLM RepairPlan before patch generation. */
public final class PlanParserOperator {

    private final AgenticRepairState state;
    private final RepairEventHub eventHub;

    public PlanParserOperator(
            AgenticRepairState state,
            RepairEventHub eventHub) {
        this.state = state;
        this.eventHub = eventHub;
    }

    @Agent(name = "parseRepairPlan", description = "Validate typed RepairPlan", outputKey = "plan")
    public RepairPlan parseRepairPlan(@V("plan") RepairPlan plan) {
        validate(plan);
        state.plan = plan;
        state.step("PlanParser", "plan", "RepairPlan validated", true);
        eventHub.publish(state.sessionId, RepairStage.PLANNING, "Repair plan generated",
                Map.of("plan", state.plan));
        return state.plan;
    }

    private void validate(RepairPlan plan) {
        if (plan == null) {
            throw new IllegalStateException("Agentic plan output was not a RepairPlan");
        }
        requireText(plan.repairTarget(), "repairTarget");
        requireText(plan.rootCauseHypothesis(), "rootCauseHypothesis");
        requireList(plan.suspectedFiles(), "suspectedFiles");
        requireList(plan.steps(), "steps");
        requireText(plan.testCommand(), "testCommand");
        if (!"mvn -pl target-service test".equals(plan.testCommand())) {
            throw new IllegalStateException("RepairPlan testCommand must be mvn -pl target-service test");
        }
        for (String file : plan.suspectedFiles()) {
            requireText(file, "suspectedFiles");
            if (!file.startsWith("target-service/src/main/") && !file.startsWith("target-service/src/test/")) {
                throw new IllegalStateException("RepairPlan suspected file is outside target-service src: " + file);
            }
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("RepairPlan " + fieldName + " is required");
        }
    }

    private void requireList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("RepairPlan " + fieldName + " must not be empty");
        }
        for (String value : values) {
            requireText(value, fieldName);
        }
    }
}
