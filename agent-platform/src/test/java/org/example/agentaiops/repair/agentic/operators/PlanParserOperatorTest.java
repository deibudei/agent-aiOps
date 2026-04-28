package org.example.agentaiops.repair.agentic.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.junit.jupiter.api.Test;

class PlanParserOperatorTest {

    @Test
    void validatesTypedRepairPlan() {
        AgenticRepairState state = new AgenticRepairState("session-001", Instant.now());
        PlanParserOperator operator = new PlanParserOperator(
                state,
                new RepairEventHub());

        RepairPlan plan = operator.parseRepairPlan(new RepairPlan(
                "OrderService.calculateUnitPrice",
                "quantity is not validated before division",
                List.of("target-service/src/main/java/com/example/targetservice/service/OrderService.java"),
                List.of("Add quantity validation"),
                "mvn -pl target-service test"));

        assertThat(plan.repairTarget()).isEqualTo("OrderService.calculateUnitPrice");
        assertThat(state.plan).isSameAs(plan);
        assertThat(state.steps).hasSize(1);
    }

    @Test
    void rejectsInvalidRepairPlan() {
        PlanParserOperator operator = new PlanParserOperator(
                new AgenticRepairState("session-001", Instant.now()),
                new RepairEventHub());

        assertThatThrownBy(() -> operator.parseRepairPlan(new RepairPlan(
                "",
                "quantity is not validated before division",
                List.of("agent-platform/src/main/java/Bad.java"),
                List.of("Add quantity validation"),
                "mvn test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RepairPlan");
    }
}
