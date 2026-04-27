package org.example.agentaiops.repair.agentic;

import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.ReviewDecision;

/** Deterministic fallbacks and small formatting helpers for the Agentic workflow. */
public final class AgenticFallbacks {

    private static final String ORDER_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/OrderService.java";

    private static final String BUGGY_METHOD = ""
            + "    public int calculateUnitPrice(int totalCents, int quantity) {\n"
            + "        return totalCents / quantity;\n"
            + "    }";

    private static final String FIXED_METHOD = ""
            + "    /** Calculates unit price and rejects invalid quantities. */\n"
            + "    public int calculateUnitPrice(int totalCents, int quantity) {\n"
            + "        if (quantity <= 0) {\n"
            + "            throw new IllegalArgumentException(\"quantity must be greater than 0\");\n"
            + "        }\n"
            + "        return totalCents / quantity;\n"
            + "    }";

    private AgenticFallbacks() {
    }

    public static RepairPlan fallbackPlan(RepairProperties properties, String evidence) {
        String hypothesis = "The order quote endpoint lacks validation for quantity <= 0, "
                + "which allows division by zero instead of returning a controlled 400 response.";
        if (evidence != null && evidence.contains("ArithmeticException")) {
            hypothesis = "Traceback shows ArithmeticException: / by zero in OrderService, "
                    + "caused by missing quantity validation.";
        }
        return new RepairPlan(
                "Parameter validation bug in target-service order quote API",
                hypothesis,
                List.of(ORDER_SERVICE),
                List.of(
                        "Read traceback and failing test evidence.",
                        "Read OrderService and locate unit price calculation.",
                        "Add explicit quantity validation before division.",
                        "Run target-service tests.",
                        "Review diff before commit, PR, and notification."),
                properties.getTargetProject().getTestCommand());
    }

    public static PatchProposal fallbackPatchProposal(String rawPatchJson) {
        return new PatchProposal(
                "Parameter validation bug in target-service order quote API",
                "OrderService divides by quantity without checking quantity <= 0.",
                List.of(new PatchOperation(
                        ORDER_SERVICE,
                        BUGGY_METHOD,
                        FIXED_METHOD,
                        "Reject invalid quantities before integer division.")),
                List.of("mvn -pl target-service test"),
                true,
                rawPatchJson == null ? "" : rawPatchJson);
    }

    public static PatchResult toPatchResult(PatchApplicationResult applicationResult) {
        String files = applicationResult.patchResults().stream()
                .map(PatchResult::filePath)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new PatchResult(applicationResult.success(), files, applicationResult.message());
    }

    public static String buildPrBody(String sessionId, RepairPlan plan, ReviewDecision reviewDecision) {
        return """
                ## Auto Repair

                Session: `%s`

                ### Plan
                %s

                ### Review
                %s
                """.formatted(sessionId, plan, reviewDecision.reason());
    }
}
