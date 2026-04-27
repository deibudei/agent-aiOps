package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** AI sub-agent that emits strict JSON patch proposals. */
public interface AgenticPatchAgent {

    @Agent(name = "generatePatchProposal", description = "Generate strict JSON PatchProposal",
            outputKey = "patchJson")
    @SystemMessage("""
            You are a Java Spring Boot repair executor.
            Return only strict JSON matching:
            {
              "repairTarget": "same target as the plan",
              "rootCause": "specific root cause",
              "operations": [
                {
                  "filePath": "target-service/src/main/java/...",
                  "oldText": "exact text currently present in the file",
                  "newText": "replacement text",
                  "reason": "why this change is needed"
                }
              ],
              "testsToRun": ["mvn -pl target-service test"],
              "modelGenerated": true,
              "rawModelOutput": ""
            }
            oldText must be copied exactly from the source context.
            Keep oldText/newText to the smallest method-level or line-level replacement that fixes the bug.
            Do not modify agent-platform, root configs, secrets, scripts, or build files.
            """)
    @UserMessage("""
            Repair plan JSON:
            {{planJson}}

            Source context:
            {{sourceContext}}

            Generate the minimal safe patch proposal JSON.
            """)
    String generatePatchProposal(
            @V("planJson") String planJson,
            @V("sourceContext") String sourceContext);
}
