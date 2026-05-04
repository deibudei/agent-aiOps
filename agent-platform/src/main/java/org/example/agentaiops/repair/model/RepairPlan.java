package org.example.agentaiops.repair.model;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Describes the Agent's intended target, root cause, steps, and validation command. */
@Description("A minimal, auditable repair plan for one target-service incident.")
public record RepairPlan(
        @Description("Short human-readable repair target in concise Simplified Chinese; keep Java symbols unchanged.")
        String repairTarget,
        @Description("Specific root-cause hypothesis in one concise Simplified Chinese sentence, grounded in evidence.")
        String rootCauseHypothesis,
        @Description("Repo-relative Java source or test files that are justified by the evidence.")
        List<String> suspectedFiles,
        @Description("2-4 concrete repair steps in concise Simplified Chinese; keep identifiers and commands unchanged.")
        List<String> steps,
        @Description("Validation command to run after the patch; normally mvn -pl target-service test.")
        String testCommand) {
}
