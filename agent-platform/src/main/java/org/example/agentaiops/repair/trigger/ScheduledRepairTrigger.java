package org.example.agentaiops.repair.trigger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.example.agentaiops.repair.service.RepairWorkflowService;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically polls the target-service health and triggers auto-repair
 * when errors are detected. Enabled via {@code REPAIR_MONITOR_ENABLED=true}.
 */
@Component
public class ScheduledRepairTrigger {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRepairTrigger.class);

    private final RepairWorkflowService repairWorkflowService;
    private final ToolPolicy toolPolicy;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String targetBaseUrl;

    private Instant lastTriggeredAt = Instant.EPOCH;
    private int consecutiveFailures = 0;

    public ScheduledRepairTrigger(
            RepairWorkflowService repairWorkflowService,
            ToolPolicy toolPolicy,
            @Value("${repair.monitor.enabled:false}") boolean enabled,
            @Value("${repair.monitor.target-base-url:http://localhost:9910}") String targetBaseUrl) {
        this.repairWorkflowService = repairWorkflowService;
        this.toolPolicy = toolPolicy;
        this.enabled = enabled;
        this.targetBaseUrl = targetBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Polls the target-service every N seconds (configurable).
     * Default: every 30 seconds when enabled.
     */
    @Scheduled(fixedDelayString = "${repair.monitor.poll-interval-seconds:30}000")
    public void pollTargetService() {
        if (!enabled) {
            return;
        }
        // Avoid re-triggering within 2 minutes of the last successful trigger
        if (Duration.between(lastTriggeredAt, Instant.now()).toMinutes() < 2) {
            return;
        }

        boolean hasError = checkHealthEndpoint() || hasNewTracebacks();

        if (hasError) {
            consecutiveFailures++;
            log.info("Target-service error detected (consecutiveFailures={})", consecutiveFailures);
            if (consecutiveFailures >= 2) {
                triggerRepair();
            }
        } else {
            consecutiveFailures = 0;
        }
    }

    private boolean checkHealthEndpoint() {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(targetBaseUrl + "/api/orders/quote?totalCents=100&quantity=1"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 500;
        } catch (Exception e) {
            log.debug("Target-service health check failed: {}", e.getMessage());
            return true; // Connection refused = service likely down with injected fault
        }
    }

    private boolean hasNewTracebacks() {
        try {
            Path tracebackDir = toolPolicy.targetLogsRoot().resolve("tracebacks");
            if (!Files.exists(tracebackDir)) {
                return false;
            }
            try (var stream = Files.list(tracebackDir)) {
                return stream.anyMatch(file -> {
                    try {
                        return Files.getLastModifiedTime(file).toInstant().isAfter(lastTriggeredAt);
                    } catch (Exception e) {
                        return false;
                    }
                });
            }
        } catch (Exception e) {
            log.debug("Could not scan traceback directory: {}", e.getMessage());
            return false;
        }
    }

    private void triggerRepair() {
        consecutiveFailures = 0;
        lastTriggeredAt = Instant.now();
        String sessionId = "auto-" + System.currentTimeMillis();
        log.info("Auto-triggering repair with sessionId={}", sessionId);
        try {
            repairWorkflowService.startAsync(sessionId);
        } catch (Exception e) {
            log.error("Auto-trigger repair failed: {}", e.getMessage(), e);
        }
    }
}
