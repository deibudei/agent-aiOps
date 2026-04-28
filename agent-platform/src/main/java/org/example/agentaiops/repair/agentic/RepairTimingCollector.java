package org.example.agentaiops.repair.agentic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import dev.langchain4j.model.output.TokenUsage;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairStepTiming;
import org.example.agentaiops.repair.model.RepairTiming;

/** Collects wall-clock display timestamps and monotonic durations for one repair run. */
public final class RepairTimingCollector {

    private static final int MAX_SUMMARY_LENGTH = 240;

    private final Instant startedAt;
    private final long startedNanos;
    private final Map<String, ActiveStep> activeSteps = new LinkedHashMap<>();
    private final Map<String, MutableModelUsage> modelUsageByStep = new LinkedHashMap<>();
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
        RepairModelUsage modelUsage = modelUsageByStep.get(stepName) == null
                ? null
                : modelUsageByStep.get(stepName).snapshot();
        completedSteps.add(new RepairStepTiming(
                stepName,
                activeStep.startedAt(),
                completedAt,
                Math.max(0, durationMillis),
                success,
                trim(summary),
                modelUsage == null ? null : modelUsage.role(),
                modelUsage == null ? null : displayModel(modelUsage),
                modelUsage == null ? null : modelUsage.inputTokenCount(),
                modelUsage == null ? null : modelUsage.outputTokenCount(),
                modelUsage == null ? null : modelUsage.totalTokenCount()));
    }

    public synchronized RepairModelUsage recordModelUsage(
            String stepName,
            String role,
            String configuredModel,
            String responseModel,
            TokenUsage tokenUsage) {
        if (stepName == null || stepName.isBlank()) {
            return null;
        }
        MutableModelUsage usage = modelUsageByStep.computeIfAbsent(stepName, ignored -> new MutableModelUsage(stepName));
        usage.add(role, configuredModel, responseModel, tokenUsage);
        return usage.snapshot();
    }

    public synchronized RepairTiming snapshot() {
        Instant completedAt = Instant.now();
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        List<RepairModelUsage> modelUsage = modelUsageByStep.values().stream()
                .map(MutableModelUsage::snapshot)
                .toList();
        return new RepairTiming(
                startedAt,
                completedAt,
                Math.max(0, durationMillis),
                List.copyOf(completedSteps),
                modelUsage);
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

    private String displayModel(RepairModelUsage usage) {
        return hasText(usage.responseModel()) ? usage.responseModel() : usage.configuredModel();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class MutableModelUsage {
        private final String stepName;
        private String role;
        private String configuredModel;
        private String responseModel;
        private int callCount;
        private Integer inputTokenCount;
        private Integer outputTokenCount;
        private Integer totalTokenCount;

        private MutableModelUsage(String stepName) {
            this.stepName = stepName;
        }

        private void add(String role, String configuredModel, String responseModel, TokenUsage tokenUsage) {
            this.role = firstText(this.role, role);
            this.configuredModel = firstText(this.configuredModel, configuredModel);
            this.responseModel = firstText(this.responseModel, responseModel);
            this.callCount++;
            if (tokenUsage != null) {
                this.inputTokenCount = sum(this.inputTokenCount, tokenUsage.inputTokenCount());
                this.outputTokenCount = sum(this.outputTokenCount, tokenUsage.outputTokenCount());
                this.totalTokenCount = sum(this.totalTokenCount, tokenUsage.totalTokenCount());
            }
        }

        private RepairModelUsage snapshot() {
            return new RepairModelUsage(
                    stepName,
                    role,
                    configuredModel,
                    responseModel,
                    callCount,
                    inputTokenCount,
                    outputTokenCount,
                    totalTokenCount);
        }

        private String firstText(String current, String candidate) {
            return current != null && !current.isBlank() ? current : candidate;
        }

        private Integer sum(Integer left, Integer right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left + right;
        }
    }
}
