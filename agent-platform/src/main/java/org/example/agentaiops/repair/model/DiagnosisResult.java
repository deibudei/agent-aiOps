package org.example.agentaiops.repair.model;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured diagnosis produced before repair planning. */
@Description("A grounded root-cause diagnosis for one target-service failure.")
public record DiagnosisResult(
        @Description("Exception type, failing assertion, or externally visible symptom.")
        String failureSignal,
        @Description("First relevant application frame, method, route, or test that localizes the fault.")
        String primaryEvidence,
        @Description("Specific root-cause hypothesis grounded in traceback, tests, or source snippets.")
        String rootCause,
        @Description("Input boundary, API contract, or service invariant that was violated.")
        String violatedContract,
        @Description("Repo-relative files that should be inspected or patched.")
        List<String> suspectedFiles,
        @Description("Confidence from 0.0 to 1.0 that the diagnosis explains the failure.")
        double confidence) {
}
