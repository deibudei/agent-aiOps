package org.example.agentaiops.repair.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.agentaiops.llm.LangChainPatchPlanner;
import org.example.agentaiops.repair.model.EvidenceBundle;
import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchProposal;
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
    private final LangChainPatchPlanner langChainPatchPlanner;

    /** Wires source-reading, patching, testing, and optional LangChain4j patch planning. */
    public RepairExecutorAgent(
            ReadCodeTools readCodeTools,
            PatchTools patchTools,
            RunTestTools runTestTools,
            LangChainPatchPlanner langChainPatchPlanner) {
        this.readCodeTools = readCodeTools;
        this.patchTools = patchTools;
        this.runTestTools = runTestTools;
        this.langChainPatchPlanner = langChainPatchPlanner;
    }

    /** Executes a plan without structured evidence, using the legacy fallback path. */
    public RepairExecutionResult execute(RepairPlan plan, int maxAttempts) {
        return execute(plan, null, maxAttempts);
    }

    /** Applies an LLM patch proposal when available, then validates with Maven tests. */
    public RepairExecutionResult execute(RepairPlan plan, EvidenceBundle evidenceBundle, int maxAttempts) {
        List<RepairStepResult> steps = new ArrayList<>();
        PatchProposal patchProposal = null;
        PatchApplicationResult patchApplicationResult = null;
        PatchResult patchResult;

        if (langChainPatchPlanner.enabled() && evidenceBundle != null) {
            Optional<PatchProposal> proposal = langChainPatchPlanner.propose(plan, evidenceBundle.sourceSnippets());
            if (proposal.isPresent()) {
                patchProposal = proposal.get();
                steps.add(new RepairStepResult(
                        "LangChainPatchPlanner",
                        "PatchProposal",
                        "operations=" + patchProposal.operations().size(),
                        !patchProposal.operations().isEmpty()));
                patchApplicationResult = patchTools.applyProposal(patchProposal);
                patchResult = toPatchResult(patchApplicationResult);
            } else {
                patchResult = new PatchResult(false, "", "LLM patch proposal was empty or invalid JSON");
                steps.add(new RepairStepResult(
                        "LangChainPatchPlanner", "PatchProposal", patchResult.message(), false));
            }
        } else {
            patchResult = executeFallbackPatch(steps);
        }

        steps.add(new RepairStepResult("PatchTools", patchResult.filePath(), patchResult.message(), patchResult.success()));

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

        return new RepairExecutionResult(
                steps, testResult, patchResult, patchProposal, patchApplicationResult);
    }

    /** Runs the original deterministic OrderService repair when LLM repair is disabled. */
    private PatchResult executeFallbackPatch(List<RepairStepResult> steps) {
        ToolExecutionResult code = readCodeTools.readFile(ORDER_SERVICE);
        steps.add(new RepairStepResult("ReadCodeTools", ORDER_SERVICE, summarize(code), code.success()));

        if (code.success() && code.output().contains("quantity must be greater than 0")) {
            return new PatchResult(true, ORDER_SERVICE, "Validation already present");
        }
        if (code.success() && code.output().contains("return totalCents / quantity;")) {
            return patchTools.replaceInFile(ORDER_SERVICE, BUGGY_METHOD, FIXED_METHOD);
        }
        return new PatchResult(false, ORDER_SERVICE, "Patch not attempted");
    }

    /** Collapses a multi-operation patch application into the legacy patch result shape. */
    private PatchResult toPatchResult(PatchApplicationResult applicationResult) {
        String files = applicationResult.patchResults().stream()
                .map(PatchResult::filePath)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new PatchResult(applicationResult.success(), files, applicationResult.message());
    }

    /** Produces short step output for the repair record and SSE events. */
    private String summarize(ToolExecutionResult result) {
        if (!result.success()) {
            return result.error();
        }
        String output = result.output();
        return output.length() <= 400 ? output : output.substring(0, 400) + "...";
    }
}
