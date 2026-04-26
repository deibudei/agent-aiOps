package org.example.agentaiops.repair.model;

import java.time.Instant;
import java.util.Map;

/** SSE payload published for each visible repair workflow stage. */
public record RepairEvent(
        String sessionId,
        String stage,
        String message,
        Instant timestamp,
        Map<String, Object> details) {

    /** Creates a timestamped event without details. */
    public static RepairEvent of(String sessionId, RepairStage stage, String message) {
        return of(sessionId, stage, message, Map.of());
    }

    /** Creates a timestamped event with structured details. */
    public static RepairEvent of(
            String sessionId, RepairStage stage, String message, Map<String, Object> details) {
        return new RepairEvent(sessionId, stage.wireName(), message, Instant.now(), details);
    }
}
