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

    public RepairReflection reflect(
            String evidence, RepairPlan plan, RepairExecutionResult executionResult, ReviewDecision reviewDecision) {
        boolean passed = reviewDecision.status() == ReviewStatus.PASS;
        String testCoverage = executionResult.testResult().success()
                ? "JUnit covered valid quote calculation and zero quantity rejection."
                : "Tests did not pass; inspect Maven output before committing.";
        return new RepairReflection(
                plan.rootCauseHypothesis(),
                summarizeEvidence(evidence),
                passed
                        ? "Added explicit input validation before the risky arithmetic operation."
                        : "Attempted targeted validation repair, but review did not allow commit.",
                testCoverage,
                passed
                        ? "For parameter validation bugs, check boundary values before arithmetic or persistence."
                        : "When repair fails, compare traceback, diff, and failing tests before retrying.",
                List.of(
                        "Search for similar arithmetic operations without boundary checks.",
                        "Add regression tests for each recovered traceback.",
                        "Keep repair writes limited to the affected service module."));
    }

    private String summarizeEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "No traceback was available; repair used failing tests and known target-service scenario.";
        }
        return evidence.length() <= 800 ? evidence : evidence.substring(0, 800) + "...";
    }
}
