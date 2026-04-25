package org.example.agentaiops.repair.model;

import java.time.Instant;
import java.util.Map;

public record RepairEvent(
        String sessionId,
        String stage,
        String message,
        Instant timestamp,
        Map<String, Object> details) {

    public static RepairEvent of(String sessionId, RepairStage stage, String message) {
        return of(sessionId, stage, message, Map.of());
    }

    public static RepairEvent of(
            String sessionId, RepairStage stage, String message, Map<String, Object> details) {
        return new RepairEvent(sessionId, stage.wireName(), message, Instant.now(), details);
    }
}
