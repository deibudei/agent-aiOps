package org.example.agentaiops.repair.agentic.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.example.agentaiops.llm.StructuredJsonParser;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.junit.jupiter.api.Test;

class PlanParserOperatorTest {

    @Test
    void parsesValidRepairPlanJson() {
        AgenticRepairState state = new AgenticRepairState("session-001", Instant.now());
        PlanParserOperator operator = new PlanParserOperator(
                state,
                new StructuredJsonParser(new ObjectMapper()),
                new RepairEventHub());

        RepairPlan plan = operator.parseRepairPlan("""
                {
                  "repairTarget": "OrderService.calculateUnitPrice",
                  "rootCauseHypothesis": "quantity is not validated before division",
                  "suspectedFiles": [
                    "target-service/src/main/java/com/example/targetservice/service/OrderService.java"
                  ],
                  "steps": ["Add quantity validation"],
                  "testCommand": "mvn -pl target-service test"
                }
                """);

        assertThat(plan.repairTarget()).isEqualTo("OrderService.calculateUnitPrice");
        assertThat(state.plan).isSameAs(plan);
        assertThat(state.steps).hasSize(1);
    }

    @Test
    void rejectsInvalidRepairPlanJson() {
        PlanParserOperator operator = new PlanParserOperator(
                new AgenticRepairState("session-001", Instant.now()),
                new StructuredJsonParser(new ObjectMapper()),
                new RepairEventHub());

        assertThatThrownBy(() -> operator.parseRepairPlan("not json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RepairPlan");
    }
}
