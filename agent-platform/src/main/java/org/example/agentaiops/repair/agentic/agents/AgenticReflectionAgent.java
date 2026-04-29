package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.ReviewDecision;

/** AI sub-agent that creates a typed, reusable repair reflection. */
public interface AgenticReflectionAgent {

    @Agent(name = "reflectRepair", description = "Generate typed RepairReflection", outputKey = "reflection")
    @SystemMessage("""
            Assume the role of a senior Java incident reviewer.
            Create a concise, evidence-grounded reflection for future repair retrieval.

            Reflection rules:
            - Ground the reflection in the actual plan, test result, review decision, PR outcome, and final outcome.
            - Do not assume the bug type. Avoid hardcoded arithmetic, route, or status-code language unless the plan says so.
            - For FAILED outcomes, explain what blocked automatic repair and what a developer should inspect next.
            - Return only the structured RepairReflection object; no markdown, comments, or prose.

            RepairReflection shape:
            {
              "rootCause": "root cause from the plan/evidence",
              "keyEvidence": "short evidence summary",
              "fixStrategy": "what was applied or attempted",
              "testCoverage": "what the tests validated or why they still failed",
              "lessonsLearned": "reusable lesson",
              "futureHints": ["short follow-up hint"]
            }
            """)
    @UserMessage("""
            Evidence:
            {{evidence}}

            Repair plan:
            {{plan}}

            Execution result:
            {{execution}}

            Review decision:
            {{review}}

            Final outcome:
            {{outcome}}

            Outcome reason:
            {{outcomeReason}}

            Generate the RepairReflection.
            """)
    RepairReflection reflectRepair(
            @V("evidence") String evidence,
            @V("plan") RepairPlan plan,
            @V("execution") RepairExecutionResult execution,
            @V("review") ReviewDecision review,
            @V("outcome") RepairOutcome outcome,
            @V("outcomeReason") String outcomeReason);
}
