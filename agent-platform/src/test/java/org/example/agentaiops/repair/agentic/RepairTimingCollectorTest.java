package org.example.agentaiops.repair.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.example.agentaiops.repair.model.RepairTiming;
import org.junit.jupiter.api.Test;

class RepairTimingCollectorTest {

    @Test
    void recordsStepAndTotalDurations() {
        RepairTimingCollector collector = new RepairTimingCollector(Instant.parse("2026-04-27T09:00:00Z"));

        collector.begin("collectEvidence");
        collector.end("collectEvidence", true, "evidence collected");

        RepairTiming timing = collector.snapshot();

        assertThat(timing.startedAt()).isEqualTo(Instant.parse("2026-04-27T09:00:00Z"));
        assertThat(timing.durationMillis()).isGreaterThanOrEqualTo(0);
        assertThat(timing.steps()).hasSize(1);
        assertThat(timing.steps().get(0).stepName()).isEqualTo("collectEvidence");
        assertThat(timing.steps().get(0).success()).isTrue();
        assertThat(timing.steps().get(0).summary()).isEqualTo("evidence collected");
    }
}
