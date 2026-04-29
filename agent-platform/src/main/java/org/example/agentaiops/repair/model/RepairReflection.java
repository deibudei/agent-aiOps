package org.example.agentaiops.repair.model;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Stores the post-repair lessons used for future retrieval or review. */
@Description("A concise post-repair reflection grounded in the actual plan, tests, review, and outcome.")
public record RepairReflection(
        @Description("Root cause summarized from the repair plan and evidence.")
        String rootCause,
        @Description("Most important traceback, test, diff, or review evidence.")
        String keyEvidence,
        @Description("Specific repair strategy that was applied or attempted.")
        String fixStrategy,
        @Description("What tests did or did not validate.")
        String testCoverage,
        @Description("Reusable lesson for future similar incidents.")
        String lessonsLearned,
        @Description("Actionable follow-up hints for future retrieval or review.")
        List<String> futureHints) {
}
