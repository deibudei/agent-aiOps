package org.example.agentaiops.repair.model;

import java.util.List;

/** Combines patch application, tool steps, and validation output for review. */
public record RepairExecutionResult(
        List<RepairStepResult> stepResults,
        TestExecutionResult testResult,
        PatchResult patchResult,
        PatchProposal patchProposal,
        PatchApplicationResult patchApplicationResult) {

    /** Keeps existing tests and fallback code concise when no LLM patch proposal exists. */
    public RepairExecutionResult(
            List<RepairStepResult> stepResults,
            TestExecutionResult testResult,
            PatchResult patchResult) {
        this(stepResults, testResult, patchResult, null, null);
    }
}
