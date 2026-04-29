package org.example.agentaiops.repair.agentic.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.agentic.agents.AgenticReflectionAgent;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.junit.jupiter.api.Test;

class ReflectOperatorTest {

    @Test
    void retriesInvalidTypedReflectionOnce() {
        AgenticRepairState state = new AgenticRepairState("reflect-001", Instant.now());
        state.evidence = "traceback";
        state.plan = new RepairPlan(
                "target",
                "root cause",
                List.of("target-service/src/main/java/App.java"),
                List.of("patch"),
                "mvn -pl target-service test");
        state.testResult = new TestExecutionResult(0, "ok", "", 10, false);
        state.reviewDecision = new ReviewDecision(ReviewStatus.PASS, "ok", "low", List.of());
        state.markOutcome(RepairOutcome.FIXED, "fixed");
        AtomicInteger calls = new AtomicInteger();
        AgenticReflectionAgent agent = (evidence, plan, execution, review, outcome, outcomeReason) -> {
            if (calls.incrementAndGet() == 1) {
                return new RepairReflection("", "", "", "", "", List.of());
            }
            return new RepairReflection("root", "evidence", "fix", "tests", "lesson", List.of("hint"));
        };
        RepairEventHub eventHub = mock(RepairEventHub.class);

        String result = new ReflectOperator(state, agent, eventHub).reflectRepair();

        assertThat(result).isEqualTo("fix");
        assertThat(calls).hasValue(2);
        verify(eventHub).publish(eq("reflect-001"), eq(RepairStage.REFLECTING),
                contains("Reflection output invalid on attempt 1"));
    }
}
