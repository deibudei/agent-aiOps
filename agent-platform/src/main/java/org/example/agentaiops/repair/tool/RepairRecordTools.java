package org.example.agentaiops.repair.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.agentaiops.repair.model.RepairRecord;
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

                ## Traceback Summary

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
                record.tracebackSummary(),
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
}
