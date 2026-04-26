package org.example.agentaiops.repair.model;

/** API response returned after a repair workflow is accepted. */
public record RepairRunResponse(String sessionId, String status, String streamUrl) {
}
