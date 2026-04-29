package org.example.agentaiops.repair.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.example.agentaiops.repair.agentic.AgenticRepairRunner;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RepairWorkflowServiceTest {

    @Test
    void publishesErrorWhenAgenticRunnerIsUnavailable() {
        RepairEventHub eventHub = mock(RepairEventHub.class);
        AgenticRepairRunner runner = mock(AgenticRepairRunner.class);
        RepairRecordTools recordTools = mock(RepairRecordTools.class);
        when(recordTools.writeRecord(any())).thenReturn(ToolExecutionResult.success("ok"));
        RepairWorkflowService service = new RepairWorkflowService(eventHub, runner, recordTools, Runnable::run);

        when(runner.available()).thenReturn(false);

        service.startAsync("session-001");

        verify(eventHub).publish("session-001", RepairStage.DETECTING, "Repair workflow accepted");
        verify(eventHub).publish(
                eq("session-001"),
                eq(RepairStage.ERROR),
                contains("REPAIR_LLM_ENABLED=true"),
                anyMap());
        verify(recordTools).writeRecord(argThat(record ->
                record.outcome() == RepairOutcome.ERROR
                        && record.timing() != null
                        && record.timing().durationMillis() >= 0));
        verify(runner, never()).run(any(), any(Instant.class));
    }

    @Test
    void publishesErrorWhenAgenticRunnerFailsWithoutFallback() {
        RepairEventHub eventHub = mock(RepairEventHub.class);
        AgenticRepairRunner runner = mock(AgenticRepairRunner.class);
        RepairRecordTools recordTools = mock(RepairRecordTools.class);
        when(recordTools.writeRecord(any())).thenReturn(ToolExecutionResult.success("ok"));
        RepairWorkflowService service = new RepairWorkflowService(eventHub, runner, recordTools, Runnable::run);

        when(runner.available()).thenReturn(true);
        doThrow(new IllegalStateException("model returned invalid patch"))
                .when(runner).run(eq("session-002"), any(Instant.class));

        service.startAsync("session-002");

        verify(eventHub).publish("session-002", RepairStage.DETECTING, "Repair workflow accepted");
        verify(eventHub).publish("session-002", RepairStage.EXECUTING,
                "Deterministic repair DAG is enabled");
        verify(eventHub).publish(
                eq("session-002"),
                eq(RepairStage.ERROR),
                contains("IllegalStateException: model returned invalid patch"),
                anyMap());
        verify(recordTools).writeRecord(argThat(record ->
                record.outcome() == RepairOutcome.ERROR
                        && record.outcomeReason().contains("model returned invalid patch")
                        && record.timing() != null
                        && record.timing().durationMillis() >= 0));
    }

    @Test
    void rejectsUnsafeSessionId() {
        RepairEventHub eventHub = mock(RepairEventHub.class);
        AgenticRepairRunner runner = mock(AgenticRepairRunner.class);
        RepairRecordTools recordTools = mock(RepairRecordTools.class);
        RepairWorkflowService service = new RepairWorkflowService(eventHub, runner, recordTools, Runnable::run);

        assertThatThrownBy(() -> service.startAsync("../escape"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sessionId");
    }
}
