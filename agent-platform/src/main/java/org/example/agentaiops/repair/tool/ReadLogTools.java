package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class ReadLogTools {

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;

    /** Wires log path configuration and read-path safety checks. */
    public ReadLogTools(RepairProperties properties, ToolPolicy toolPolicy) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
    }

    /** Reads the latest traceback-like tail from the target-service log. */
    public ToolExecutionResult readLatestTraceback(int maxChars) {
        try {
            Path logPath = toolPolicy.resolveForRead(properties.getTargetProject().getLogPath());
            if (!Files.exists(logPath)) {
                return ToolExecutionResult.failure("Log path does not exist: " + toolPolicy.display(logPath));
            }

            if (Files.isDirectory(logPath)) {
                return readLatestTracebackFromDirectory(logPath, maxChars);
            }

            return readTracebackFile(logPath, maxChars);
        } catch (IOException | IllegalArgumentException e) {
            return ToolExecutionResult.failure(e.getMessage());
        }
    }

    private ToolExecutionResult readLatestTracebackFromDirectory(Path logDirectory, int maxChars) throws IOException {
        try (Stream<Path> stream = Files.walk(logDirectory, 4)) {
            List<Path> candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isReadableLogFile)
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .toList();
            if (candidates.isEmpty()) {
                return ToolExecutionResult.failure("No log files found in log directory: "
                        + toolPolicy.display(logDirectory));
            }

            for (Path candidate : candidates) {
                ToolExecutionResult result = readTracebackFile(candidate, maxChars);
                if (result.success()) {
                    return result;
                }
            }
            return ToolExecutionResult.failure("No exception marker found in log directory: "
                    + toolPolicy.display(logDirectory));
        }
    }

    private ToolExecutionResult readTracebackFile(Path logPath, int maxChars) throws IOException {
        String content = Files.readString(logPath);
        if (content.isBlank()) {
            return ToolExecutionResult.failure("Log file is empty: " + toolPolicy.display(logPath));
        }

        int markerIndex = bestExceptionMarker(content);
        if (markerIndex < 0) {
            return ToolExecutionResult.failure("No exception marker found in log file: " + toolPolicy.display(logPath));
        }

        String traceback = extractTraceback(content, markerIndex, maxChars);
        return ToolExecutionResult.success("Log source: %s%n%n%s".formatted(toolPolicy.display(logPath), traceback));
    }

    /** Keeps the traceback from the error header instead of drifting into framework stack frames. */
    private String extractTraceback(String content, int markerIndex, int maxChars) {
        int limit = Math.max(1000, maxChars);
        int end = Math.min(content.length(), markerIndex + limit);
        return content.substring(markerIndex, end).trim();
    }

    private boolean isReadableLogFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".log") || name.endsWith(".txt") || name.endsWith(".trace");
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Finds the best line to start traceback evidence from. */
    private int bestExceptionMarker(String content) {
        int metadataException = lastLineContaining(content, "exception=");
        if (metadataException >= 0) {
            return metadataException;
        }

        int markerIndex = -1;
        int lineStart = 0;
        while (lineStart < content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            if (isExceptionHeader(line) || isErrorLogLine(line)) {
                markerIndex = lineStart;
            }
            lineStart = lineEnd + 1;
        }
        return markerIndex;
    }

    private int lastLineContaining(String content, String marker) {
        int markerIndex = -1;
        int lineStart = 0;
        while (lineStart < content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            if (line.contains(marker)) {
                markerIndex = lineStart;
            }
            lineStart = lineEnd + 1;
        }
        return markerIndex;
    }

    private boolean isExceptionHeader(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("at ") || trimmed.startsWith("... ")) {
            return false;
        }
        return trimmed.startsWith("Caused by:")
                || trimmed.matches(".*\\b[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)*(Exception|Error|Throwable)(:.*)?");
    }

    private boolean isErrorLogLine(String line) {
        String trimmed = line.trim();
        return line.contains(" ERROR ") || trimmed.startsWith("ERROR ");
    }
}
