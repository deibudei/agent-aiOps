package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import org.example.agentaiops.llm.StructuredJsonParser;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Parses LLM patch JSON into a typed PatchProposal. */
public final class PatchParserOperator {

    private final AgenticRepairState state;
    private final StructuredJsonParser jsonParser;
    private final RepairEventHub eventHub;

    public PatchParserOperator(AgenticRepairState state, StructuredJsonParser jsonParser, RepairEventHub eventHub) {
        this.state = state;
        this.jsonParser = jsonParser;
        this.eventHub = eventHub;
    }

    @Agent(name = "parsePatchProposal", description = "Parse strict JSON into PatchProposal",
            outputKey = "patchProposal")
    public PatchProposal parsePatchProposal(@V("patchJson") String patchJson) {
        state.patchJson = patchJson;
        PatchProposal proposal = jsonParser.parse(patchJson, PatchProposal.class)
                .orElseThrow(() -> new IllegalStateException("Agentic patch output was not valid PatchProposal JSON"));
        state.patchProposal = new PatchProposal(
                proposal.repairTarget(),
                proposal.rootCause(),
                proposal.operations(),
                proposal.testsToRun(),
                true,
                patchJson);
        boolean hasOperations = state.patchProposal.operations() != null
                && !state.patchProposal.operations().isEmpty();
        if (!hasOperations) {
            throw new IllegalStateException("Agentic patch output did not include any patch operations");
        }
        state.step("PatchParser", "patchJson", "operations="
                + (state.patchProposal.operations() == null ? 0 : state.patchProposal.operations().size()),
                hasOperations);
        eventHub.publish(state.sessionId, RepairStage.EXECUTING,
                "Patch proposal parsed with operations="
                        + (state.patchProposal.operations() == null ? 0 : state.patchProposal.operations().size()));
        return state.patchProposal;
    }
}
