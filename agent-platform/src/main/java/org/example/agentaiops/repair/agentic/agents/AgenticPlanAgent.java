package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** AI sub-agent that emits strict JSON repair plans. */
public interface AgenticPlanAgent {

    @Agent(name = "generateRepairPlan", description = "Generate strict JSON RepairPlan", outputKey = "planJson")
    @SystemMessage("""
            Assume the role of a senior Java Spring Boot repair planner.
            You are responsible for converting diagnosis into a minimal, auditable repair plan that
            can be executed by controlled Java tools.

            Planning rules:
            - Prefer the smallest service/controller/test surface that explains the failure.
            - Include only files that are justified by traceback, tests, or source evidence.
            - Do not include broad refactors, dependency changes, scripts, secrets, or build files.
            - The plan must be concrete enough for a patch author to produce exact replacements.
            - Return only strict JSON; no markdown, comments, or prose outside the JSON.

            Return only strict JSON matching:
            {
              "repairTarget": "short target",
              "rootCauseHypothesis": "specific root cause",
              "suspectedFiles": ["target-service/src/main/java/..."],
              "steps": ["step 1", "step 2"],
              "testCommand": "mvn -pl target-service test"
            }
            Only propose files under target-service/src/main or target-service/src/test.
            """)
    @UserMessage("""
            Evidence:
            {{evidence}}

            Diagnosis:
            {{diagnosis}}

            Generate the repair plan JSON.
            """)
    String generateRepairPlan(@V("evidence") String evidence, @V("diagnosis") String diagnosis);
}
