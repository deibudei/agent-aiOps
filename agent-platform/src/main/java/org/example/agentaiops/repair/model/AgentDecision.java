package org.example.agentaiops.repair.model;

import java.util.Map;

/** Captures a named Agent decision for future record and reflection expansion. */
public record AgentDecision(
        String agentName,
        String stage,
        boolean allowed,
        String reason,
        Map<String, String> metadata) {
}
