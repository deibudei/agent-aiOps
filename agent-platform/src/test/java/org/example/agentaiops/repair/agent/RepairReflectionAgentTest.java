package org.example.agentaiops.repair.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairStepResult;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.junit.jupiter.api.Test;

class RepairReflectionAgentTest {

    @Test
    void createsReflectionForSuccessfulRepair() {
        RepairReflectionAgent agent = new RepairReflectionAgent();
        RepairPlan plan = new RepairPlan(
                "target",
                "missing validation",
                List.of("target-service/src/main/java/App.java"),
                List.of("patch"),
                "mvn -pl target-service test");
        RepairExecutionResult execution = new RepairExecutionResult(
                List.of(new RepairStepResult("RunTestTools", "tests", "ok", true)),
                new TestExecutionResult(0, "ok", "", 10, false),
                new PatchResult(true, "App.java", "patched"));
        ReviewDecision review = new ReviewDecision(
                ReviewStatus.PASS,
                "ok",
                "low",
                List.of("target-service/src/main/java/App.java"));

        RepairReflection reflection = agent.reflect("ArithmeticException", plan, execution, review);

        assertThat(reflection.rootCause()).contains("missing validation");
        assertThat(reflection.futureHints()).isNotEmpty();
    }
}
