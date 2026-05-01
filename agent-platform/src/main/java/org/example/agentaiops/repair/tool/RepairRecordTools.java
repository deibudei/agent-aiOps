package org.example.agentaiops.repair.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairRecordIndex;
import org.example.agentaiops.repair.model.RepairRecordSummary;
import org.example.agentaiops.repair.model.RepairStepTiming;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class RepairRecordTools {

    private static final Pattern SAFE_SESSION_ID = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private final ToolPolicy toolPolicy;
    private final ObjectMapper objectMapper;

    /** Prepares record writing with workspace paths and Java time JSON support. */
    public RepairRecordTools(ToolPolicy toolPolicy, ObjectMapper objectMapper) {
        this.toolPolicy = toolPolicy;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    /** Writes the repair record as both JSON for machines and Markdown for demos. */
    public ToolExecutionResult writeRecord(RepairRecord record) {
        try {
            Path recordsDir = recordsDir();
            Files.createDirectories(recordsDir);
            String safeSessionId = safeSessionId(record.sessionId());
            Path jsonPath = ensureUnderRecords(recordsDir, recordsDir.resolve(safeSessionId + ".json"));
            Path mdPath = ensureUnderRecords(recordsDir, recordsDir.resolve(safeSessionId + ".md"));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), record);
            Files.writeString(mdPath, toMarkdown(record));
            return ToolExecutionResult.success("Wrote " + jsonPath + " and " + mdPath);
        } catch (IOException | IllegalArgumentException e) {
            return ToolExecutionResult.failure(e.getMessage());
        }
    }

    /** Lists compact repair record summaries sorted by newest completion time. */
    public RepairRecordIndex listRecordSummaries() {
        Path recordsDir = recordsDir();
        if (!Files.isDirectory(recordsDir)) {
            return new RepairRecordIndex(0, List.of());
        }
        try (var stream = Files.list(recordsDir)) {
            List<RepairRecordSummary> summaries = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .map(this::readSummary)
                    .filter(summary -> summary != null)
                    .sorted(Comparator
                            .comparing(
                                    RepairRecordSummary::completedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(RepairRecordSummary::sessionId))
                    .toList();
            return new RepairRecordIndex(summaries.size(), summaries);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list repair records: " + e.getMessage(), e);
        }
    }

    /** Reads one repair record from the shared repair-records directory. */
    public Optional<RepairRecord> readRecord(String sessionId) {
        try {
            Path path = jsonPath(sessionId);
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(path.toFile(), RepairRecord.class));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Returns the display path for one JSON repair record. */
    public String jsonRecordPath(String sessionId) {
        return toolPolicy.display(jsonPath(sessionId));
    }

    /** Returns the display path for one Markdown repair record. */
    public String markdownRecordPath(String sessionId) {
        return toolPolicy.display(markdownPath(sessionId));
    }

    private RepairRecordSummary readSummary(Path path) {
        try {
            RepairRecord record = objectMapper.readValue(path.toFile(), RepairRecord.class);
            return toSummary(record, toolPolicy.display(path));
        } catch (IOException e) {
            return null;
        }
    }

    private Path recordsDir() {
        return toolPolicy.homeWorkspaceRoot().resolve("repair-records").normalize();
    }

    private Path jsonPath(String sessionId) {
        String safeSessionId = safeSessionId(sessionId);
        Path recordsDir = recordsDir();
        return ensureUnderRecords(recordsDir, recordsDir.resolve(safeSessionId + ".json"));
    }

    private Path markdownPath(String sessionId) {
        String safeSessionId = safeSessionId(sessionId);
        Path recordsDir = recordsDir();
        return ensureUnderRecords(recordsDir, recordsDir.resolve(safeSessionId + ".md"));
    }

    private RepairRecordSummary toSummary(RepairRecord record, String recordPath) {
        Long durationMillis = record.timing() == null ? null : record.timing().durationMillis();
        return new RepairRecordSummary(
                record.sessionId(),
                record.outcome(),
                record.outcomeReason(),
                record.startedAt(),
                record.completedAt(),
                durationMillis,
                patchAttempts(record),
                totalTokens(record),
                record.testResult() == null ? null : record.testResult().success(),
                record.testResult() == null ? null : record.testResult().exitCode(),
                record.pullRequestResult() == null ? "" : record.pullRequestResult().url(),
                record.notificationResult() == null ? null : record.notificationResult().success(),
                recordPath);
    }

    /** Converts the main repair facts into a readable Markdown report. */
    private String toMarkdown(RepairRecord record) {
        String pr = record.pullRequestResult() == null ? "" : record.pullRequestResult().url();
        return """
                # Auto Repair Record

                - Session: %s
                - Started: %s
                - Completed: %s
                - Outcome: %s
                - Outcome reason: %s
                - PR: %s
                - Duration: %s ms

                ## Traceback Summary

                %s

                ## Timing

                %s

                ## Model Usage

                %s

                ## Evidence Summary

                %s

                ## Plan

                %s

                ## Patch Proposal

                %s

                ## Diff Summary

                ```diff
                %s
                ```

                ## Review

                %s

                ## Reflection

                - Root cause: %s
                - Fix strategy: %s
                - Lessons learned: %s
                """.formatted(
                record.sessionId(),
                record.startedAt(),
                record.completedAt(),
                record.outcome(),
                record.outcomeReason(),
                pr,
                record.timing() == null ? "n/a" : record.timing().durationMillis(),
                record.tracebackSummary(),
                timingTable(record),
                modelUsageTable(record),
                record.evidenceBundle() == null ? "" : record.evidenceBundle().summary(),
                record.plan(),
                record.patchProposal() == null ? "" : record.patchProposal(),
                trim(record.diffSummary(), 4000),
                record.reviewDecision(),
                record.reflection().rootCause(),
                record.reflection().fixStrategy(),
                record.reflection().lessonsLearned());
    }

    /** Trims long diff or evidence text for Markdown readability. */
    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "\n... trimmed ...";
    }

    private int patchAttempts(RepairRecord record) {
        if (record.stepResults() == null || record.stepResults().isEmpty()) {
            return record.patchProposal() == null ? 0 : 1;
        }
        long parserAttempts = record.stepResults().stream()
                .filter(step -> "PatchParser".equals(step.toolName()))
                .count();
        if (parserAttempts > 0) {
            return Math.toIntExact(parserAttempts);
        }
        long applyAttempts = record.stepResults().stream()
                .filter(step -> "PatchTools".equals(step.toolName()))
                .count();
        if (applyAttempts > 0) {
            return Math.toIntExact(applyAttempts);
        }
        return record.patchProposal() == null ? 0 : 1;
    }

    private Long totalTokens(RepairRecord record) {
        if (record.timing() == null || record.timing().modelUsage() == null
                || record.timing().modelUsage().isEmpty()) {
            return null;
        }
        boolean hasAnyToken = false;
        long total = 0L;
        for (RepairModelUsage usage : record.timing().modelUsage()) {
            Integer value = usage.totalTokenCount();
            if (value != null) {
                hasAnyToken = true;
                total += value;
            }
        }
        return hasAnyToken ? total : null;
    }

    private String timingTable(RepairRecord record) {
        if (record.timing() == null || record.timing().steps() == null || record.timing().steps().isEmpty()) {
            return "No timing data recorded.";
        }
        StringBuilder table = new StringBuilder();
        table.append("| Step | Duration ms | Success | Model | Input tokens | Output tokens | Total tokens | Started | Completed | Summary |\n");
        table.append("| --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- |\n");
        for (RepairStepTiming step : record.timing().steps()) {
            table.append("| ")
                    .append(markdownCell(step.stepName()))
                    .append(" | ")
                    .append(step.durationMillis())
                    .append(" | ")
                    .append(step.success())
                    .append(" | ")
                    .append(markdownCell(step.modelName()))
                    .append(" | ")
                    .append(numberCell(step.inputTokenCount()))
                    .append(" | ")
                    .append(numberCell(step.outputTokenCount()))
                    .append(" | ")
                    .append(numberCell(step.totalTokenCount()))
                    .append(" | ")
                    .append(step.startedAt())
                    .append(" | ")
                    .append(step.completedAt())
                    .append(" | ")
                    .append(markdownCell(step.summary()))
                    .append(" |\n");
        }
        return table.toString();
    }

    private String modelUsageTable(RepairRecord record) {
        if (record.timing() == null || record.timing().modelUsage() == null
                || record.timing().modelUsage().isEmpty()) {
            return "No model usage data recorded.";
        }
        StringBuilder table = new StringBuilder();
        table.append("| Step | Role | Configured model | Response model | Calls | Input tokens | Output tokens | Total tokens |\n");
        table.append("| --- | --- | --- | --- | ---: | ---: | ---: | ---: |\n");
        for (RepairModelUsage usage : record.timing().modelUsage()) {
            table.append("| ")
                    .append(markdownCell(usage.stepName()))
                    .append(" | ")
                    .append(markdownCell(usage.role()))
                    .append(" | ")
                    .append(markdownCell(usage.configuredModel()))
                    .append(" | ")
                    .append(markdownCell(usage.responseModel()))
                    .append(" | ")
                    .append(usage.callCount())
                    .append(" | ")
                    .append(numberCell(usage.inputTokenCount()))
                    .append(" | ")
                    .append(numberCell(usage.outputTokenCount()))
                    .append(" | ")
                    .append(numberCell(usage.totalTokenCount()))
                    .append(" |\n");
        }
        return table.toString();
    }

    private String numberCell(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeSessionId(String sessionId) {
        if (sessionId == null || !SAFE_SESSION_ID.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("Invalid sessionId for repair record: " + sessionId);
        }
        return sessionId;
    }

    private Path ensureUnderRecords(Path recordsDir, Path path) {
        Path normalizedRecordsDir = recordsDir.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRecordsDir)) {
            throw new IllegalArgumentException("Repair record path escapes repair-records: " + normalizedPath);
        }
        return normalizedPath;
    }

    private String markdownCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace('\r', ' ').replace('\n', ' ').trim();
    }
}
