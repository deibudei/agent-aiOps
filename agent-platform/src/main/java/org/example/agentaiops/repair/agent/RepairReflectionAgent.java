package org.example.agentaiops.repair.agent;

import java.util.List;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.springframework.stereotype.Component;

@Component
public class RepairReflectionAgent {

    /** Summarizes the repair outcome into reusable lessons and follow-up hints. */
    public RepairReflection reflect(
            String evidence, RepairPlan plan, RepairExecutionResult executionResult, ReviewDecision reviewDecision) {
        boolean passed = reviewDecision.status() == ReviewStatus.PASS;
        String testCoverage = executionResult.testResult().success()
                ? "Configured target-service tests passed after the attempted repair."
                : "Tests did not pass; inspect Maven output before committing.";
        return new RepairReflection(
                plan.rootCauseHypothesis(),
                summarizeEvidence(evidence),
                passed
                        ? "Applied the planned target-service change and passed review."
                        : "Attempted the planned target-service change, but review did not allow commit.",
                testCoverage,
                passed
                        ? "Keep future repairs tied to the failing evidence, narrow diff, and passing regression tests."
                        : "When repair fails, compare traceback, diff, and failing tests before retrying.",
                List.of(
                        "Search for files that share the same failing contract or stack-frame pattern.",
                        "Add regression tests for each recovered traceback.",
                        "Keep repair writes limited to the affected service module."));
    }

    /** Keeps the stored evidence summary short enough for records and notifications. */
    private String summarizeEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "No traceback was available; repair used failing tests and known target-service scenario.";
        }
        return evidence.length() <= 800 ? evidence : evidence.substring(0, 800) + "...";
    }
}
