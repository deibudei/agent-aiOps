package org.example.agentaiops.repair.agentic;

/** Captures a target-service file's content before a patch is applied so reflexion can roll back. */
public record PatchSnapshot(String filePath, String contentBeforeApply) {
}
