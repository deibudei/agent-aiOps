package org.example.agentaiops.repair.model;

import java.util.List;

/** Carries model-proposed patch operations before Java tools apply them. */
public record PatchProposal(
        String repairTarget,
        String rootCause,
        List<PatchOperation> operations,
        List<String> testsToRun,
        boolean modelGenerated,
        String rawModelOutput) {
}
