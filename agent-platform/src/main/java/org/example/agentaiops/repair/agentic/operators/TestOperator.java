package org.example.agentaiops.repair.agentic.operators;

import java.util.Map;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.RunTestTools;

/** Runs target-service tests once. Reflexion retries are driven by AgenticRepairRunner. */
public final class TestOperator {

    private final AgenticRepairState state;
    private final RunTestTools runTestTools;
    private final RepairProperties properties;
    private final RepairEventHub eventHub;

    public TestOperator(
            AgenticRepairState state,
            RunTestTools runTestTools,
            RepairProperties properties,
            RepairEventHub eventHub) {
        this.state = state;
        this.runTestTools = runTestTools;
        this.properties = properties;
        this.eventHub = eventHub;
    }

    public TestExecutionResult runTargetTests() {
        state.testResult = runTestTools.runTargetServiceTests();
        state.step("RunTestTools", properties.getTargetProject().getTestCommand(),
                "exitCode=" + state.testResult.exitCode()
                        + ", durationMs=" + state.testResult.durationMillis(),
                state.testResult.success());
        eventHub.publish(state.sessionId, RepairStage.TESTING, "Target-service tests completed",
                Map.of("testResult", state.testResult));
        return state.testResult;
    }
}
