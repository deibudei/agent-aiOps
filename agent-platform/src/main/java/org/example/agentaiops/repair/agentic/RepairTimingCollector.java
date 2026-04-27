package org.example.agentaiops.repair.agentic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.example.agentaiops.repair.model.RepairStepTiming;
import org.example.agentaiops.repair.model.RepairTiming;

/** Collects wall-clock display timestamps and monotonic durations for one repair run. */
public final class RepairTimingCollector {

    private static final int MAX_SUMMARY_LENGTH = 240;

    private final Instant startedAt;
    private final long startedNanos;
    private final Map<String, ActiveStep> activeSteps = new HashMap<>();
    private final List<RepairStepTiming> completedSteps = new ArrayList<>();

    public RepairTimingCollector(Instant startedAt) {
        this.startedAt = startedAt;
        this.startedNanos = System.nanoTime();
    }

    public synchronized void begin(String stepName) {
        activeSteps.put(stepName, new ActiveStep(Instant.now(), System.nanoTime()));
    }

    public synchronized void end(String stepName, boolean success, String summary) {
        ActiveStep activeStep = activeSteps.remove(stepName);
        if (activeStep == null) {
            return;
        }
        Instant completedAt = Instant.now();
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - activeStep.startedNanos());
        completedSteps.add(new RepairStepTiming(
                stepName,
                activeStep.startedAt(),
                completedAt,
                Math.max(0, durationMillis),
                success,
                trim(summary)));
    }

    public synchronized RepairTiming snapshot() {
        Instant completedAt = Instant.now();
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return new RepairTiming(startedAt, completedAt, Math.max(0, durationMillis), List.copyOf(completedSteps));
    }

    private String trim(String summary) {
        if (summary == null) {
            return "";
        }
        String cleaned = summary.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() <= MAX_SUMMARY_LENGTH
                ? cleaned
                : cleaned.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    private record ActiveStep(Instant startedAt, long startedNanos) {
    }
}
