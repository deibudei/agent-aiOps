package org.example.agentaiops.repair.model;

import java.util.List;

public record RepairReflection(
        String rootCause,
        String keyEvidence,
        String fixStrategy,
        String testCoverage,
        String lessonsLearned,
        List<String> futureHints) {
}
