package org.example.agentaiops.repair.model;

/** Reports one exact-text patch attempt against one file. */
public record PatchResult(boolean success, String filePath, String message) {
}
