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
            Assume the role of a careful Java Spring Boot patch author.
            You are responsible for producing a minimal, exact, machine-applicable patch proposal.
            Treat the provided source context as the only writable truth.

            Patch rules:
            - Fix the root cause directly at the narrowest safe boundary.
            - Preserve existing public API behavior unless the repair plan requires validation.
            - Copy oldText exactly from source context, including whitespace and line breaks.
            - Keep each replacement method-level or smaller when possible.
            - Include tests only when source context shows the expected regression surface.
            - Do not invent files or modify agent-platform, root configs, secrets, scripts, or build files.
            - Return only strict JSON; no markdown, comments, or prose outside the JSON.

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
