package org.example.agentaiops.repair.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairStepTiming;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class RepairRecordTools {

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
            Path recordsDir = toolPolicy.workspaceRoot().resolve("repair-records").normalize();
            Files.createDirectories(recordsDir);
            Path jsonPath = recordsDir.resolve(record.sessionId() + ".json");
            Path mdPath = recordsDir.resolve(record.sessionId() + ".md");

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), record);
            Files.writeString(mdPath, toMarkdown(record));
            return ToolExecutionResult.success("Wrote " + jsonPath + " and " + mdPath);
        } catch (IOException e) {
            return ToolExecutionResult.failure(e.getMessage());
        }
    }

    /** Converts the main repair facts into a readable Markdown report. */
    private String toMarkdown(RepairRecord record) {
        String pr = record.pullRequestResult() == null ? "" : record.pullRequestResult().url();
        return """
                # Auto Repair Record

                - Session: %s
                - Started: %s
                - Completed: %s
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

    private String markdownCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace('\r', ' ').replace('\n', ' ').trim();
    }
}
