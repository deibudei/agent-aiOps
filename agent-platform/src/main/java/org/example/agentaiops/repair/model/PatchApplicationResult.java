package org.example.agentaiops.repair.model;

import java.util.List;

/** Summarizes all patch operations applied through PatchTools. */
public record PatchApplicationResult(
        boolean success,
        List<PatchResult> patchResults,
        String message) {
}
