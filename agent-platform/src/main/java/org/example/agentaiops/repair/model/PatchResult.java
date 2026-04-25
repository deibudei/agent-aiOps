package org.example.agentaiops.repair.model;

public record PatchResult(boolean success, String filePath, String message) {
}
