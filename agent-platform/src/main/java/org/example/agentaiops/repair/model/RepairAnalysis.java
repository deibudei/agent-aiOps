package org.example.agentaiops.repair.model;

import java.util.List;

/** Structured root-cause analysis contract for the real Agent phase. */
public record RepairAnalysis(
        String rootCause,
        String failureMode,
        List<String> suspectedFiles,
        String risk,
        boolean modelGenerated,
        String rawModelOutput) {
}
