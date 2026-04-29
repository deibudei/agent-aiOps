package org.example.agentaiops.repair.agentic;

import org.example.agentaiops.repair.model.EvidenceBundle;
import org.example.agentaiops.repair.model.SourceSnippet;

/** Builds bounded prompt context for the deterministic DAG and AI sub-agents. */
public final class AgenticEvidenceFormatter {

    public static final int AGENTIC_TRACEBACK_CHARS = 2500;
    public static final int AGENTIC_FILE_CHARS = 2500;
    public static final int AGENTIC_SEARCH_CHARS = 2000;
    private static final int AGENTIC_SOURCE_FILES = 4;
    private static final int AGENTIC_SOURCE_CHARS_PER_FILE = 1600;

    private AgenticEvidenceFormatter() {
    }

    public static String compactEvidence(EvidenceBundle evidenceBundle) {
        if (evidenceBundle == null) {
            return "";
        }
        String tracebackText = evidenceBundle.traceback() == null
                ? ""
                : evidenceBundle.traceback().success()
                        ? evidenceBundle.traceback().output()
                        : evidenceBundle.traceback().error();
        String testText = evidenceBundle.baselineTestResult() == null
                ? ""
                : evidenceBundle.baselineTestResult().stdout();
        String candidateFiles = evidenceBundle.candidateFiles() == null
                ? ""
                : String.join(System.lineSeparator(), evidenceBundle.candidateFiles());
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
                evidenceBundle.summary(),
                trim(tracebackText, AGENTIC_TRACEBACK_CHARS),
                trim(testText, 1200),
                candidateFiles);
    }

    public static String sourceContext(EvidenceBundle evidenceBundle) {
        if (evidenceBundle == null || evidenceBundle.sourceSnippets() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (SourceSnippet snippet : evidenceBundle.sourceSnippets().stream().limit(AGENTIC_SOURCE_FILES).toList()) {
            builder.append("FILE: ").append(snippet.path()).append(System.lineSeparator());
            builder.append("ROLE: ").append(snippet.role()).append(System.lineSeparator());
            builder.append(trim(snippet.content(), AGENTIC_SOURCE_CHARS_PER_FILE))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    public static String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n... trimmed ...";
    }
}
