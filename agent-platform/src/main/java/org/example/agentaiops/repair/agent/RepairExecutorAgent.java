package org.example.agentaiops.repair.agent;

import java.util.ArrayList;
import java.util.List;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairStepResult;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.tool.PatchTools;
import org.example.agentaiops.repair.tool.ReadCodeTools;
import org.example.agentaiops.repair.tool.RunTestTools;
import org.springframework.stereotype.Component;

@Component
public class RepairExecutorAgent {

    private static final String ORDER_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/OrderService.java";

    private static final String BUGGY_METHOD = ""
            + "    public int calculateUnitPrice(int totalCents, int quantity) {\n"
            + "        return totalCents / quantity;\n"
            + "    }";

    private static final String FIXED_METHOD = ""
            + "    public int calculateUnitPrice(int totalCents, int quantity) {\n"
            + "        if (quantity <= 0) {\n"
            + "            throw new IllegalArgumentException(\"quantity must be greater than 0\");\n"
            + "        }\n"
            + "        return totalCents / quantity;\n"
            + "    }";

    private final ReadCodeTools readCodeTools;
    private final PatchTools patchTools;
    private final RunTestTools runTestTools;

    public RepairExecutorAgent(
            ReadCodeTools readCodeTools, PatchTools patchTools, RunTestTools runTestTools) {
        this.readCodeTools = readCodeTools;
        this.patchTools = patchTools;
        this.runTestTools = runTestTools;
    }

    public RepairExecutionResult execute(RepairPlan plan, int maxAttempts) {
        List<RepairStepResult> steps = new ArrayList<>();

        ToolExecutionResult code = readCodeTools.readFile(ORDER_SERVICE);
        steps.add(new RepairStepResult("ReadCodeTools", ORDER_SERVICE, summarize(code), code.success()));

        PatchResult patchResult = new PatchResult(false, ORDER_SERVICE, "Patch not attempted");
        if (code.success() && code.output().contains("quantity must be greater than 0")) {
            patchResult = new PatchResult(true, ORDER_SERVICE, "Validation already present");
        } else if (code.success() && code.output().contains("return totalCents / quantity;")) {
            patchResult = patchTools.replaceInFile(ORDER_SERVICE, BUGGY_METHOD, FIXED_METHOD);
        }
        steps.add(new RepairStepResult("PatchTools", ORDER_SERVICE, patchResult.message(), patchResult.success()));

        TestExecutionResult testResult = runTestTools.runTargetServiceTests();
        steps.add(new RepairStepResult(
                "RunTestTools",
                plan.testCommand(),
                "exitCode=" + testResult.exitCode() + ", durationMs=" + testResult.durationMillis(),
                testResult.success()));

        int attempts = 1;
        while (!testResult.success() && attempts < maxAttempts && patchResult.success()) {
            attempts++;
            testResult = runTestTools.runTargetServiceTests();
            steps.add(new RepairStepResult(
                    "RunTestTools",
                    "retry " + attempts,
                    "exitCode=" + testResult.exitCode() + ", durationMs=" + testResult.durationMillis(),
                    testResult.success()));
        }

        return new RepairExecutionResult(steps, testResult, patchResult);
    }

    private String summarize(ToolExecutionResult result) {
        if (!result.success()) {
            return result.error();
        }
        String output = result.output();
        return output.length() <= 400 ? output : output.substring(0, 400) + "...";
    }
}
