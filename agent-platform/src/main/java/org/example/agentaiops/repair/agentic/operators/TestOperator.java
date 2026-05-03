package org.example.agentaiops.repair.agentic.operators;

import java.util.LinkedHashMap;
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
        String command = properties.getTargetProject().getTestCommand();
        eventHub.publish(state.sessionId, RepairStage.TESTING, "RunTest started: " + command,
                toolDetails("tool_started", "RunTest", command, "running", true, "Running target-service tests", null));
        state.testResult = runTestTools.runTargetServiceTests();
        state.step("RunTestTools", command,
                "exitCode=" + state.testResult.exitCode()
                        + ", durationMs=" + state.testResult.durationMillis(),
                state.testResult.success());
        eventHub.publish(state.sessionId, RepairStage.TESTING, "Target-service tests completed",
                toolDetails(
                        "tool_completed",
                        "RunTest",
                        command,
                        state.testResult.success() ? "completed" : "failed",
                        state.testResult.success(),
                        "exitCode=" + state.testResult.exitCode()
                                + ", durationMs=" + state.testResult.durationMillis(),
                        state.testResult));
        return state.testResult;
    }

    private Map<String, Object> toolDetails(
            String eventType,
            String toolName,
            String target,
            String status,
            boolean success,
            String summary,
            TestExecutionResult testResult) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", eventType);
        details.put("toolName", toolName);
        details.put("target", target);
        details.put("status", status);
        details.put("success", success);
        details.put("summary", summary);
        if (testResult != null) {
            details.put("exitCode", testResult.exitCode());
            details.put("durationMillis", testResult.durationMillis());
            details.put("testResult", testResult);
        }
        return details;
    }
}
