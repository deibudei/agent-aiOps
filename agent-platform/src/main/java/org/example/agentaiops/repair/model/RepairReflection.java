package org.example.agentaiops.repair.model;

import java.util.List;

/** Stores the post-repair lessons used for future retrieval or review. */
public record RepairReflection(
        String rootCause,
        String keyEvidence,
        String fixStrategy,
        String testCoverage,
        String lessonsLearned,
        List<String> futureHints) {
}
