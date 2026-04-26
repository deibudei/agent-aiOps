package org.example.agentaiops.repair.agent;

import java.util.List;
import java.util.Optional;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.llm.LangChainRepairPlanner;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.tool.RepairToolRegistry;
import org.springframework.stereotype.Component;

@Component
public class RepairPlannerAgent {

    private static final String ORDER_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/OrderService.java";

    private final RepairProperties properties;
    private final RepairToolRegistry toolRegistry;
    private final LangChainRepairPlanner langChainRepairPlanner;

    /** Wires the fallback planner, tool registry, and optional LangChain4j planner. */
    public RepairPlannerAgent(
            RepairProperties properties,
            RepairToolRegistry toolRegistry,
            LangChainRepairPlanner langChainRepairPlanner) {
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.langChainRepairPlanner = langChainRepairPlanner;
    }

    /** Produces a repair plan, preferring LangChain4j LLM output when enabled. */
    public RepairPlan plan(String tracebackOrEvidence) {
        Optional<RepairPlan> llmPlan = langChainRepairPlanner.plan(
                tracebackOrEvidence,
                toolRegistry.toolNames(),
                properties.getTargetProject().getTestCommand());
        if (llmPlan.isPresent()) {
            return llmPlan.get();
        }
        return fallbackPlan(tracebackOrEvidence);
    }

    /** Keeps the demo runnable when the LLM is disabled or returns invalid JSON. */
    private RepairPlan fallbackPlan(String tracebackOrEvidence) {
        String hypothesis = "The order quote endpoint lacks validation for quantity <= 0, "
                + "which allows division by zero instead of returning a controlled 400 response.";
        if (tracebackOrEvidence != null && tracebackOrEvidence.contains("ArithmeticException")) {
            hypothesis = "Traceback shows ArithmeticException: / by zero in OrderService, "
                    + "caused by missing quantity validation.";
        }

        return new RepairPlan(
                "Parameter validation bug in target-service order quote API",
                hypothesis,
                List.of(ORDER_SERVICE),
                List.of(
                        "Read the latest target-service traceback or failing test output.",
                        "Read OrderService and locate unit price calculation.",
                        "Add explicit quantity validation before division.",
                        "Run the target-service Maven test suite.",
                        "Send diff and test result to reviewer."),
                properties.getTargetProject().getTestCommand()
                        + " using tools " + String.join(", ", toolRegistry.toolNames()));
    }
}
