package org.example.agentaiops.repair.agentic.operators;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.agentaiops.repair.agentic.AgenticOutputFormatter;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.agentic.PatchSnapshot;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.PatchTools;
import org.example.agentaiops.repair.tool.ToolPolicy;

/** Applies safe exact-text patch operations through PatchTools. */
public final class PatchApplyOperator {

    private final AgenticRepairState state;
    private final PatchTools patchTools;
    private final ToolPolicy toolPolicy;
    private final RepairEventHub eventHub;

    public PatchApplyOperator(
            AgenticRepairState state, PatchTools patchTools, ToolPolicy toolPolicy, RepairEventHub eventHub) {
        this.state = state;
        this.patchTools = patchTools;
        this.toolPolicy = toolPolicy;
        this.eventHub = eventHub;
    }

    public PatchResult applyPatchProposal(PatchProposal patchProposal) {
        state.patchProposal = patchProposal;
        List<PatchSnapshot> snapshots = snapshotBeforeApply(patchProposal);
        state.patchApplicationResult = patchTools.applyProposal(patchProposal);
        state.patchResult = AgenticOutputFormatter.toPatchResult(state.patchApplicationResult);
        if (state.patchApplicationResult.success()) {
            state.recordRollbackSnapshots(snapshots);
        }
        state.step("PatchTools", state.patchResult.filePath(), state.patchResult.message(),
                state.patchResult.success());
        eventHub.publish(state.sessionId, RepairStage.PATCHING, state.patchResult.message(),
                Map.of("patch", state.patchResult));
        return state.patchResult;
    }

    /** Restores files captured by the most recent successful apply. */
    public boolean rollbackLastApply() {
        List<PatchSnapshot> snapshots = state.popRollbackSnapshots();
        if (snapshots == null || snapshots.isEmpty()) {
            return false;
        }
        boolean success = true;
        for (PatchSnapshot snapshot : snapshots) {
            try {
                Files.writeString(toolPolicy.resolveForWrite(snapshot.filePath()), snapshot.contentBeforeApply());
            } catch (IOException | IllegalArgumentException e) {
                success = false;
                eventHub.publish(state.sessionId, RepairStage.PATCHING,
                        "Rollback failed for " + snapshot.filePath() + ": " + e.getMessage());
            }
        }
        state.step("PatchRollback", "rollbackLastApply",
                "files=" + snapshots.size() + ", success=" + success, success);
        eventHub.publish(state.sessionId, RepairStage.PATCHING,
                "Rolled back " + snapshots.size() + " file(s) before re-patching",
                Map.of("rollback", success));
        return success;
    }

    private List<PatchSnapshot> snapshotBeforeApply(PatchProposal proposal) {
        List<PatchSnapshot> snapshots = new ArrayList<>();
        if (proposal == null || proposal.operations() == null) {
            return snapshots;
        }
        Set<String> seen = new HashSet<>();
        for (PatchOperation operation : proposal.operations()) {
            if (operation == null || operation.filePath() == null || !seen.add(operation.filePath())) {
                continue;
            }
            try {
                String content = Files.readString(toolPolicy.resolveForWrite(operation.filePath()));
                snapshots.add(new PatchSnapshot(operation.filePath(), content));
            } catch (IOException | IllegalArgumentException e) {
                eventHub.publish(state.sessionId, RepairStage.PATCHING,
                        "Snapshot before apply failed for " + operation.filePath() + ": " + e.getMessage());
            }
        }
        return snapshots;
    }
}
