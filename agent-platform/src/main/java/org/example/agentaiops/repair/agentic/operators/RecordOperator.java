package org.example.agentaiops.repair.agentic.operators;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.example.agentaiops.repair.agentic.AgenticEvidenceFormatter;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.GitTools;
import org.example.agentaiops.repair.tool.RepairRecordTools;

/** Persists the final repair record after reflection (single write). */
public final class RecordOperator {

    private final AgenticRepairState state;
    private final RepairRecordTools repairRecordTools;
    private final GitTools gitTools;
    private final RepairEventHub eventHub;

    public RecordOperator(
            AgenticRepairState state, RepairRecordTools repairRecordTools, GitTools gitTools, RepairEventHub eventHub) {
        this.state = state;
        this.repairRecordTools = repairRecordTools;
        this.gitTools = gitTools;
        this.eventHub = eventHub;
    }

    public Integer writeRepairRecord() {
        if (state.evidenceBundle == null || state.plan == null
                || state.testResult == null || state.reviewDecision == null || state.reflection == null) {
            throw new IllegalStateException("Repair state is incomplete: evidence/plan/test/review/reflection required");
        }
        state.beginTiming("writeRepairRecord");
        RepairRecord record;
        try {
            record = buildRecord();
            ToolExecutionResult write = repairRecordTools.writeRecord(record);
            if (!write.success()) {
                throw new IllegalStateException(write.error());
            }
            state.endTiming("writeRepairRecord", true, write.output());
        } catch (RuntimeException e) {
            state.endTiming("writeRepairRecord", false, e.getMessage());
            throw e;
        }
        state.recordWritten = true;
        eventHub.publish(state.sessionId, RepairStage.REFLECTING,
                "Repair record written",
                Map.of(
                        "recordVersion", record.recordVersion(),
                        "stepName", "writeRepairRecord",
                        "durationMillis", stepDurationMillis(record, "writeRepairRecord")));
        return record.recordVersion();
    }

    private RepairRecord buildRecord() {
        if (state.diff == null) {
            state.diff = gitTools.readTargetDiff();
        }
        return new RepairRecord(
                1,
                state.sessionId,
                state.startedAt,
                Instant.now(),
                state.evidenceBundle,
                AgenticEvidenceFormatter.trim(state.evidence, 3000),
                state.plan,
                List.copyOf(state.steps),
                state.patchProposal,
                state.patchApplicationResult,
                AgenticEvidenceFormatter.trim(state.diff, 6000),
                state.testResult,
                state.reviewDecision,
                state.gitCommitResult,
                state.pullRequestResult,
                state.notificationResult,
                state.reflection,
                state.timing());
    }

    private long stepDurationMillis(RepairRecord record, String stepName) {
        if (record.timing() == null) {
            return 0;
        }
        return record.timing().steps().stream()
                .filter(step -> stepName.equals(step.stepName()))
                .reduce((first, second) -> second)
                .map(step -> step.durationMillis())
                .orElse(0L);
    }
}
