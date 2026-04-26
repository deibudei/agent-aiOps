package org.example.agentaiops.repair.model;

import java.time.Instant;
import java.util.List;

/** Groups traceback, baseline tests, and source snippets for Agent planning. */
public record EvidenceBundle(
        Instant collectedAt,
        ToolExecutionResult traceback,
        TestExecutionResult baselineTestResult,
        boolean baselineTestsRun,
        List<String> candidateFiles,
        List<SourceSnippet> sourceSnippets,
        String summary) {

    /** Builds the text sent to the planner from all collected evidence. */
    public String plannerInput() {
        String tracebackText = traceback == null
                ? ""
                : traceback.success() ? traceback.output() : traceback.error();
        String testText = baselineTestResult == null ? "" : baselineTestResult.stdout();
        return """
                Summary:
                %s

                Traceback:
                %s

                Baseline test output:
                %s

                Candidate files:
                %s
                """.formatted(
                summary,
                trim(tracebackText, 5000),
                trim(testText, 5000),
                String.join(System.lineSeparator(), candidateFiles));
    }

    /** Trims large evidence blocks before prompt construction. */
    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n... trimmed ...";
    }
}
