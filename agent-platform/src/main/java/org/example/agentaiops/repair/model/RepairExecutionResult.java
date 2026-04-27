package org.example.agentaiops.repair.model;

import java.util.List;

/** Combines patch application, tool steps, and validation output for review. */
public record RepairExecutionResult(
        List<RepairStepResult> stepResults,
        TestExecutionResult testResult,
        PatchResult patchResult,
        PatchProposal patchProposal,
        PatchApplicationResult patchApplicationResult) {

    /** Keeps tests and operator code concise when only the patch result shape is needed. */
    public RepairExecutionResult(
            List<RepairStepResult> stepResults,
            TestExecutionResult testResult,
            PatchResult patchResult) {
        this(stepResults, testResult, patchResult, null, null);
    }
}
