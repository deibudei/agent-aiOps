package org.example.agentaiops.repair.agent;

import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.tool.RepairToolRegistry;
import org.springframework.stereotype.Component;

@Component
public class RepairPlannerAgent {

    private static final String ORDER_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/OrderService.java";

    private final RepairProperties properties;
    private final RepairToolRegistry toolRegistry;

    public RepairPlannerAgent(RepairProperties properties, RepairToolRegistry toolRegistry) {
        this.properties = properties;
        this.toolRegistry = toolRegistry;
    }

    public RepairPlan plan(String tracebackOrEvidence) {
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
