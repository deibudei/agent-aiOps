package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.RepairPlan;

/**
 * AI sub-agent used in the reflexion loop. Given a previously failed PatchProposal and the
 * resulting test stderr, it produces a new PatchProposal against the rolled-back source context.
 */
public interface AgenticPatchRegenerationAgent {

    @Agent(name = "regeneratePatchProposal",
            description = "Regenerate a PatchProposal after the previous attempt failed target-service tests",
            outputKey = "patchProposal")
    @SystemMessage("""
            Assume the role of a careful Java Spring Boot patch author performing a reflexion retry.
            Your previous PatchProposal was applied but target-service tests still failed. You must
            propose a new minimal, exact, machine-applicable PatchProposal that fixes BOTH the original
            root cause and the new failure surfaced by the test output.

            Reflexion rules:
            - The previous patch has already been rolled back. Treat the source context as the truth.
            - Copy oldText exactly from the source context, including whitespace and line breaks.
            - Do not repeat the previous failing edit verbatim; adapt to the failing test signal.
            - Keep changes within target-service/src/main or target-service/src/test.
            - Only emit the structured PatchProposal object; no markdown, comments, or prose.

            PatchProposal shape is the same as the first attempt.
            """)
    @UserMessage("""
            Repair plan:
            {{plan}}

            Source context (after rollback, this is the current truth on disk):
            {{sourceContext}}

            Previous PatchProposal that did not fix the bug:
            {{previousProposal}}

            Target-service test output (stdout + stderr) showing the remaining failure:
            {{testStderr}}

            Generate a new minimal PatchProposal that addresses the failure above.
            """)
    PatchProposal regeneratePatchFromTestFailure(
            @V("plan") RepairPlan plan,
            @V("sourceContext") String sourceContext,
            @V("previousProposal") PatchProposal previousProposal,
            @V("testStderr") String testStderr);
}
