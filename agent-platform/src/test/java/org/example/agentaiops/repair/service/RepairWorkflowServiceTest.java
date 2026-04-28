package org.example.agentaiops.repair.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.example.agentaiops.repair.agentic.AgenticRepairRunner;
import org.example.agentaiops.repair.model.RepairStage;
import org.junit.jupiter.api.Test;

class RepairWorkflowServiceTest {

    @Test
    void publishesErrorWhenAgenticRunnerIsUnavailable() {
        RepairEventHub eventHub = mock(RepairEventHub.class);
        AgenticRepairRunner runner = mock(AgenticRepairRunner.class);
        RepairWorkflowService service = new RepairWorkflowService(eventHub, runner, Runnable::run);

        when(runner.available()).thenReturn(false);

        service.startAsync("session-001");

        verify(eventHub).publish("session-001", RepairStage.DETECTING, "Repair workflow accepted");
        verify(eventHub).publish(
                eq("session-001"),
                eq(RepairStage.ERROR),
                contains("REPAIR_LLM_ENABLED=true"));
        verify(runner, never()).run(any(), any(Instant.class));
    }

    @Test
    void publishesErrorWhenAgenticRunnerFailsWithoutFallback() {
        RepairEventHub eventHub = mock(RepairEventHub.class);
        AgenticRepairRunner runner = mock(AgenticRepairRunner.class);
        RepairWorkflowService service = new RepairWorkflowService(eventHub, runner, Runnable::run);

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
                contains("IllegalStateException: model returned invalid patch"));
    }
}
