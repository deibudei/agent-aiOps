package org.example.agentaiops.repair.model;

/** Carries a bounded source file snippet into the planner prompt. */
public record SourceSnippet(
        String path,
        String role,
        String content,
        boolean selected,
        String selectionReason) {
}
