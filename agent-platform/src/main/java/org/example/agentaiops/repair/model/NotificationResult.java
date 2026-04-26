package org.example.agentaiops.repair.model;

/** Reports the result of optional Feishu notification delivery. */
public record NotificationResult(boolean success, String message) {
}
