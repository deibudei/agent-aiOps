package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Validates the typed LLM PatchProposal before safe application. */
public final class PatchParserOperator {

    private final AgenticRepairState state;
    private final RepairEventHub eventHub;

    public PatchParserOperator(AgenticRepairState state, RepairEventHub eventHub) {
        this.state = state;
        this.eventHub = eventHub;
    }

    @Agent(name = "parsePatchProposal", description = "Validate typed PatchProposal",
            outputKey = "patchProposal")
    public PatchProposal parsePatchProposal(@V("patchProposal") PatchProposal proposal) {
        validate(proposal);
        state.patchProposal = new PatchProposal(
                proposal.repairTarget(),
                proposal.rootCause(),
                proposal.operations(),
                proposal.testsToRun(),
                true,
                proposal.rawModelOutput() == null ? "" : proposal.rawModelOutput());
        state.step("PatchParser", "patchProposal", "operations=" + state.patchProposal.operations().size(), true);
        eventHub.publish(state.sessionId, RepairStage.EXECUTING,
                "Patch proposal validated with operations=" + state.patchProposal.operations().size());
        return state.patchProposal;
    }

    private void validate(PatchProposal proposal) {
        if (proposal == null) {
            throw new IllegalStateException("Agentic patch output was not a PatchProposal");
        }
        requireText(proposal.repairTarget(), "repairTarget");
        requireText(proposal.rootCause(), "rootCause");
        if (proposal.operations() == null || proposal.operations().isEmpty()) {
            throw new IllegalStateException("Agentic patch output did not include any patch operations");
        }
        if (proposal.testsToRun() == null || proposal.testsToRun().isEmpty()) {
            throw new IllegalStateException("PatchProposal testsToRun must not be empty");
        }
        if (!proposal.testsToRun().contains("mvn -pl target-service test")) {
            throw new IllegalStateException("PatchProposal testsToRun must include mvn -pl target-service test");
        }
        for (PatchOperation operation : proposal.operations()) {
            validateOperation(operation);
        }
    }

    private void validateOperation(PatchOperation operation) {
        if (operation == null) {
            throw new IllegalStateException("PatchProposal operation must not be null");
        }
        requireText(operation.filePath(), "filePath");
        requireText(operation.oldText(), "oldText");
        requireText(operation.newText(), "newText");
        requireText(operation.reason(), "reason");
        if (!operation.filePath().startsWith("target-service/src/main/")
                && !operation.filePath().startsWith("target-service/src/test/")) {
            throw new IllegalStateException("PatchProposal file is outside target-service src: "
                    + operation.filePath());
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PatchProposal " + fieldName + " is required");
        }
    }
}
