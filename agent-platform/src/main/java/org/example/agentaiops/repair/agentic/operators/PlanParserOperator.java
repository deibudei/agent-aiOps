package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Map;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.llm.StructuredJsonParser;
import org.example.agentaiops.repair.agentic.AgenticFallbacks;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Parses LLM plan JSON into a typed RepairPlan. */
public final class PlanParserOperator {

    private final AgenticRepairState state;
    private final StructuredJsonParser jsonParser;
    private final RepairProperties properties;
    private final RepairEventHub eventHub;

    public PlanParserOperator(
            AgenticRepairState state,
            StructuredJsonParser jsonParser,
            RepairProperties properties,
            RepairEventHub eventHub) {
        this.state = state;
        this.jsonParser = jsonParser;
        this.properties = properties;
        this.eventHub = eventHub;
    }

    @Agent(name = "parseRepairPlan", description = "Parse strict JSON into RepairPlan", outputKey = "plan")
    public RepairPlan parseRepairPlan(@V("planJson") String planJson) {
        state.planJson = planJson;
        state.plan = jsonParser.parse(planJson, RepairPlan.class)
                .orElseGet(() -> AgenticFallbacks.fallbackPlan(properties, state.evidence));
        boolean parsed = jsonParser.parse(planJson, RepairPlan.class).isPresent();
        state.step("PlanParser", "planJson", parsed ? "RepairPlan parsed" : "Fallback plan used", true);
        eventHub.publish(state.sessionId, RepairStage.PLANNING, "Repair plan generated",
                Map.of("plan", state.plan));
        return state.plan;
    }
}
