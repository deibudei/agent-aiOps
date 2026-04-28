package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.RepairPlan;

/** AI sub-agent that emits structured patch proposals. */
public interface AgenticPatchAgent {

    @Agent(name = "generatePatchProposal", description = "Generate typed PatchProposal",
            outputKey = "patchProposal")
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
            - Return only the structured PatchProposal object; no markdown, comments, or prose.

            PatchProposal shape:
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

            Few-shot example 1:
            Repair plan: target is OrderService.calculateUnitPrice quantity validation.
            Source context contains:
            public int calculateUnitPrice(int totalCents, int quantity) {
                return totalCents / quantity;
            }
            PatchProposal:
            {
              "repairTarget": "OrderService.calculateUnitPrice quantity validation",
              "rootCause": "quantity is divided before positive-boundary validation",
              "operations": [
                {
                  "filePath": "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                  "oldText": "    /** Calculates unit price without guarding invalid quantities. */\\n    public int calculateUnitPrice(int totalCents, int quantity) {\\n        return totalCents / quantity;\\n    }",
                  "newText": "    /** Calculates unit price, rejecting non-positive quantities. */\\n    public int calculateUnitPrice(int totalCents, int quantity) {\\n        if (quantity <= 0) {\\n            throw new IllegalArgumentException(\\"quantity must be positive\\");\\n        }\\n        return totalCents / quantity;\\n    }",
                  "reason": "Rejects zero or negative quantity before integer division."
                }
              ],
              "testsToRun": ["mvn -pl target-service test"],
              "modelGenerated": true,
              "rawModelOutput": ""
            }

            Few-shot example 2:
            Repair plan: restore OrderController quote route.
            Source context contains @GetMapping("/api/orders/quotation") above quote(...).
            PatchProposal:
            {
              "repairTarget": "OrderController quote route mapping",
              "rootCause": "quote route mapping drifted from /api/orders/quote",
              "operations": [
                {
                  "filePath": "target-service/src/main/java/com/example/targetservice/controller/OrderController.java",
                  "oldText": "    @GetMapping(\\"/api/orders/quotation\\")",
                  "newText": "    @GetMapping(\\"/api/orders/quote\\")",
                  "reason": "Restores the public endpoint expected by tests and callers."
                }
              ],
              "testsToRun": ["mvn -pl target-service test"],
              "modelGenerated": true,
              "rawModelOutput": ""
            }
            """)
    @UserMessage("""
            Repair plan:
            {{plan}}

            Source context:
            {{sourceContext}}

            Generate the minimal safe PatchProposal.
            """)
    PatchProposal generatePatchProposal(
            @V("plan") RepairPlan plan,
            @V("sourceContext") String sourceContext);
}
