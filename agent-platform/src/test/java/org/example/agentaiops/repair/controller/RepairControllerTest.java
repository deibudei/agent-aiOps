package org.example.agentaiops.repair.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.service.RepairWorkflowService;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RepairControllerTest {

    private final RepairWorkflowService repairWorkflowService = mock(RepairWorkflowService.class);
    private final RepairEventHub repairEventHub = mock(RepairEventHub.class);
    private final RepairRecordTools repairRecordTools = mock(RepairRecordTools.class);
    private final RepairController controller =
            new RepairController(repairWorkflowService, repairEventHub, repairRecordTools);

    @Test
    void returnsFullRepairRecord() {
        RepairRecord record = record("demo-001");
        when(repairRecordTools.readRecord("demo-001")).thenReturn(Optional.of(record));

        RepairRecord result = controller.record("demo-001");

        assertThat(result.sessionId()).isEqualTo("demo-001");
        assertThat(result.outcome()).isEqualTo(RepairOutcome.FIXED);
    }

    @Test
    void returnsNotFoundWhenRepairRecordIsMissing() {
        when(repairRecordTools.readRecord("missing-session")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.record("missing-session"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Repair record not found");
    }

    private RepairRecord record(String sessionId) {
        Instant now = Instant.parse("2026-04-30T08:00:00Z");
        return new RepairRecord(
                1,
                sessionId,
                now,
                now.plusSeconds(1),
                RepairOutcome.FIXED,
                "Patch passed review and PR was created",
                null,
                "traceback",
                null,
                List.of(),
                null,
                null,
                "",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
