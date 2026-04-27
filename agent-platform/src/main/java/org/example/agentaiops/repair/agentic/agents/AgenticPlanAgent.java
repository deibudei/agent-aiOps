package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** AI sub-agent that emits strict JSON repair plans. */
public interface AgenticPlanAgent {

    @Agent(name = "generateRepairPlan", description = "Generate strict JSON RepairPlan", outputKey = "planJson")
    @SystemMessage("""
            You are a Java Spring Boot repair planning agent.
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
