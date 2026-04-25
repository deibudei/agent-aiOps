package org.example.agentaiops.repair.model;

import java.util.List;

public record RepairExecutionResult(
        List<RepairStepResult> stepResults,
        TestExecutionResult testResult,
        PatchResult patchResult) {
}
