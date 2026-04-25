package org.example.agentaiops.repair.model;

import java.util.List;

public record RepairPlan(
        String repairTarget,
        String rootCauseHypothesis,
        List<String> suspectedFiles,
        List<String> steps,
        String testCommand) {
}
