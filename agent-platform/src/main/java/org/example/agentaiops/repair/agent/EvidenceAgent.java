package org.example.agentaiops.repair.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.example.agentaiops.repair.model.EvidenceBundle;
import org.example.agentaiops.repair.model.SourceSnippet;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.tool.ReadLogTools;
import org.example.agentaiops.repair.tool.RunTestTools;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.springframework.stereotype.Component;

@Component
public class EvidenceAgent {

    private static final int MAX_CANDIDATE_FILES = 6;
    private static final int MAX_SNIPPET_CHARS = 2400;

    private final ReadLogTools readLogTools;
    private final RunTestTools runTestTools;
    private final ToolPolicy toolPolicy;

    /** Wires log, test, and path-policy tools used for evidence collection. */
    public EvidenceAgent(ReadLogTools readLogTools, RunTestTools runTestTools, ToolPolicy toolPolicy) {
        this.readLogTools = readLogTools;
        this.runTestTools = runTestTools;
        this.toolPolicy = toolPolicy;
    }

    /** Collects traceback, optional baseline test output, and ranked source snippets. */
    public EvidenceBundle collect() {
        ToolExecutionResult traceback = readLogTools.readLatestTraceback(6000);
        TestExecutionResult baseline = null;
        boolean baselineTestsRun = false;

        if (!traceback.success()) {
            baseline = runTestTools.runTargetServiceTests();
            baselineTestsRun = true;
        }

        String evidenceText = evidenceText(traceback, baseline);
        List<RankedSourceFile> rankedFiles = discoverJavaFiles().stream()
                .map(path -> rank(path, evidenceText))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(RankedSourceFile::score)
                        .reversed()
                        .thenComparing(RankedSourceFile::displayPath))
                .limit(MAX_CANDIDATE_FILES)
                .toList();

        List<String> candidateFiles = rankedFiles.stream()
                .map(RankedSourceFile::displayPath)
                .toList();
        List<SourceSnippet> snippets = rankedFiles.stream()
                .map(this::toSnippet)
                .toList();

        return new EvidenceBundle(
                Instant.now(),
                traceback,
                baseline,
                baselineTestsRun,
                candidateFiles,
                snippets,
                summarize(traceback, baseline, candidateFiles));
    }

    /** Finds Java files under the target service source tree. */
    private List<Path> discoverJavaFiles() {
        Path sourceRoot = toolPolicy.targetRoot().resolve("src").normalize();
        if (!Files.exists(sourceRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Scores a source file by how strongly it matches traceback or test evidence. */
    private RankedSourceFile rank(Path path, String evidenceText) {
        try {
            String displayPath = toolPolicy.display(path);
            String fileName = path.getFileName().toString();
            String className = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
            String lowerPath = displayPath.toLowerCase(Locale.ROOT);
            String lowerEvidence = evidenceText.toLowerCase(Locale.ROOT);
            int score = 0;

            if (!lowerEvidence.isBlank()) {
                if (lowerEvidence.contains(className.toLowerCase(Locale.ROOT))) {
                    score += 12;
                }
                if (lowerEvidence.contains(displayPath.toLowerCase(Locale.ROOT))) {
                    score += 8;
                }
                if (lowerEvidence.contains(className.replace("Test", "").toLowerCase(Locale.ROOT))) {
                    score += 4;
                }
            }
            if (lowerPath.contains("/service/")) {
                score += 3;
            }
            if (lowerPath.contains("/controller/")) {
                score += 2;
            }
            if (lowerPath.contains("/test/")) {
                score += 1;
            }

            return new RankedSourceFile(path, displayPath, score);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Converts a ranked file into a bounded snippet for LLM context. */
    private SourceSnippet toSnippet(RankedSourceFile sourceFile) {
        String content;
        try {
            content = Files.readString(sourceFile.path());
        } catch (IOException e) {
            content = "Unable to read source file: " + e.getMessage();
        }
        return new SourceSnippet(
                sourceFile.displayPath(),
                role(sourceFile.displayPath()),
                trim(content, MAX_SNIPPET_CHARS),
                true,
                sourceFile.score() > 0
                        ? "Ranked by matching traceback/test evidence"
                        : "Included as target-service source context");
    }

    /** Creates a compact human-readable summary for events and records. */
    private String summarize(
            ToolExecutionResult traceback, TestExecutionResult baseline, List<String> candidateFiles) {
        String tracebackStatus = traceback.success() ? "traceback found" : "traceback unavailable";
        String testStatus = baseline == null
                ? "baseline tests not run"
                : baseline.success() ? "baseline tests pass" : "baseline tests fail";
        return "Evidence collected: %s, %s, candidates=%s"
                .formatted(tracebackStatus, testStatus, candidateFiles);
    }

    /** Joins all failure signals into a single text block for ranking. */
    private String evidenceText(ToolExecutionResult traceback, TestExecutionResult baseline) {
        String tracebackText = traceback.success() ? traceback.output() : traceback.error();
        String testText = baseline == null ? "" : baseline.stdout();
        return tracebackText + System.lineSeparator() + testText;
    }

    /** Labels source snippets so prompts can distinguish service, controller, and test files. */
    private String role(String displayPath) {
        String lowerPath = displayPath.toLowerCase(Locale.ROOT);
        if (lowerPath.contains("/src/test/")) {
            return "test";
        }
        if (lowerPath.contains("/controller/")) {
            return "controller";
        }
        if (lowerPath.contains("/service/")) {
            return "service";
        }
        return "source";
    }

    /** Keeps source snippets small enough for model prompts and SSE events. */
    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n... trimmed ...";
    }

    /** Keeps a discovered file together with its prompt-selection score. */
    private record RankedSourceFile(Path path, String displayPath, int score) {
    }
}
