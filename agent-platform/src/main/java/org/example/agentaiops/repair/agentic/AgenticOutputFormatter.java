package org.example.agentaiops.repair.agentic;

import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.ReviewDecision;

/** Small formatting helpers for Agentic repair outputs. */
public final class AgenticOutputFormatter {

    private AgenticOutputFormatter() {
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
