package org.example.agentaiops.repair.model;

import java.util.List;

/** Describes the Agent's intended target, root cause, steps, and validation command. */
public record RepairPlan(
        String repairTarget,
        String rootCauseHypothesis,
        List<String> suspectedFiles,
        List<String> steps,
        String testCommand) {
}
