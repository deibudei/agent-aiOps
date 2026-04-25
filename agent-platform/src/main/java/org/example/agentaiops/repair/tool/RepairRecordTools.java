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

    public RepairRecordTools(ToolPolicy toolPolicy, ObjectMapper objectMapper) {
        this.toolPolicy = toolPolicy;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

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

                ## Plan

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
                record.plan(),
                trim(record.diffSummary(), 4000),
                record.reviewDecision(),
                record.reflection().rootCause(),
                record.reflection().fixStrategy(),
                record.reflection().lessonsLearned());
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "\n... trimmed ...";
    }
}
