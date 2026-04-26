package org.example.agentaiops.repair.model;

/** Describes one safe exact-text replacement requested by the model. */
public record PatchOperation(
        String filePath,
        String oldText,
        String newText,
        String reason) {
}
