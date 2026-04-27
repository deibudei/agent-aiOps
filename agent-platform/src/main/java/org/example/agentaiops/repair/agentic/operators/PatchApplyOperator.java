package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticFallbacks;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.PatchTools;

/** Applies safe exact-text patch operations through PatchTools. */
public final class PatchApplyOperator {

    private final AgenticRepairState state;
    private final PatchTools patchTools;
    private final RepairEventHub eventHub;

    public PatchApplyOperator(AgenticRepairState state, PatchTools patchTools, RepairEventHub eventHub) {
        this.state = state;
        this.patchTools = patchTools;
        this.eventHub = eventHub;
    }

    @Agent(name = "applyPatchProposal", description = "Apply safe exact-text patch operations",
            outputKey = "patchResult")
    public PatchResult applyPatchProposal(@V("patchProposal") PatchProposal patchProposal) {
        state.patchProposal = patchProposal;
        state.patchApplicationResult = patchTools.applyProposal(patchProposal);
        state.patchResult = AgenticFallbacks.toPatchResult(state.patchApplicationResult);
        state.step("PatchTools", state.patchResult.filePath(), state.patchResult.message(),
                state.patchResult.success());
        eventHub.publish(state.sessionId, RepairStage.PATCHING, state.patchResult.message(),
                Map.of("patch", state.patchResult));
        return state.patchResult;
    }
}
