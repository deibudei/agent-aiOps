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
            - Human-facing fields repairTarget, rootCause, and operations[].reason must be concise Simplified Chinese.
            - Keep oldText and newText as exact source code text; do not translate code, comments, strings, paths, or commands.
            - Escape line breaks inside JSON string fields as \\n; never put raw line breaks inside oldText or newText JSON strings.
            - Return only the structured PatchProposal object; no markdown, comments, or prose.

            PatchProposal shape:
            {
              "repairTarget": "Chinese target from the plan",
              "rootCause": "Chinese root cause",
              "operations": [
                {
                  "filePath": "target-service/src/main/java/...",
                  "oldText": "exact text currently present in the file",
                  "newText": "replacement text",
                  "reason": "Chinese reason for this change"
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
              "repairTarget": "补齐 OrderService.calculateUnitPrice 的 quantity 参数校验",
              "rootCause": "quantity 为 0 时先执行除法，触发 ArithmeticException。",
              "operations": [
                {
                  "filePath": "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                  "oldText": "    /** Calculates unit price without guarding invalid quantities. */\\n    public int calculateUnitPrice(int totalCents, int quantity) {\\n        return totalCents / quantity;\\n    }",
                  "newText": "    /** Calculates unit price, rejecting non-positive quantities. */\\n    public int calculateUnitPrice(int totalCents, int quantity) {\\n        if (quantity <= 0) {\\n            throw new IllegalArgumentException(\\"quantity must be positive\\");\\n        }\\n        return totalCents / quantity;\\n    }",
                  "reason": "在整数除法前拒绝 0 或负数 quantity，避免 / by zero。"
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
              "repairTarget": "恢复 OrderController.quote 的公开路由",
              "rootCause": "quote 路由偏离 /api/orders/quote 契约。",
              "operations": [
                {
                  "filePath": "target-service/src/main/java/com/example/targetservice/controller/OrderController.java",
                  "oldText": "    @GetMapping(\\"/api/orders/quotation\\")",
                  "newText": "    @GetMapping(\\"/api/orders/quote\\")",
                  "reason": "恢复测试和调用方期望的公开接口。"
                }
              ],
              "testsToRun": ["mvn -pl target-service test"],
              "modelGenerated": true,
              "rawModelOutput": ""
            }
            """)
    @UserMessage("""
            Dynamic few-shot examples from past successful repairs:
            {{fewShots}}

            Repair plan:
            {{plan}}

            Source context:
            {{sourceContext}}

            Generate the minimal safe PatchProposal. Follow the patterns shown in the dynamic few-shot examples above.
            Human-facing fields must be concise Simplified Chinese.
            """)
    PatchProposal generatePatchProposal(
            @V("plan") RepairPlan plan,
            @V("sourceContext") String sourceContext,
            @V("fewShots") String fewShots);
}
