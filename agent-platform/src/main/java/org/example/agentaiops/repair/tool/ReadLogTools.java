package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class ReadLogTools {

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;

    public ReadLogTools(RepairProperties properties, ToolPolicy toolPolicy) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
    }

    public ToolExecutionResult readLatestTraceback(int maxChars) {
        try {
            Path logPath = toolPolicy.resolveForRead(properties.getTargetProject().getLogPath());
            if (!Files.exists(logPath)) {
                return ToolExecutionResult.failure("Log file does not exist: " + toolPolicy.display(logPath));
            }

            String content = Files.readString(logPath);
            if (content.isBlank()) {
                return ToolExecutionResult.failure("Log file is empty: " + toolPolicy.display(logPath));
            }

            String traceback = extractTail(content, maxChars);
            return ToolExecutionResult.success(traceback);
        } catch (IOException | IllegalArgumentException e) {
            return ToolExecutionResult.failure(e.getMessage());
        }
    }

    private String extractTail(String content, int maxChars) {
        int limit = Math.max(1000, maxChars);
        int start = Math.max(0, content.length() - limit);
        String tail = content.substring(start);
        int exceptionIndex = Math.max(
                Math.max(tail.lastIndexOf("Exception"), tail.lastIndexOf("ERROR")),
                tail.lastIndexOf("java.lang"));
        if (exceptionIndex > 0) {
            int lineStart = tail.lastIndexOf('\n', exceptionIndex);
            if (lineStart >= 0) {
                return tail.substring(lineStart + 1).trim();
            }
        }
        return tail.trim();
    }
}
