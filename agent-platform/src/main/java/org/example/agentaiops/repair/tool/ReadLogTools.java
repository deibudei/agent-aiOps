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

        int markerIndex = lastExceptionMarker(content);
        if (markerIndex < 0) {
            return ToolExecutionResult.failure("No exception marker found in log file: " + toolPolicy.display(logPath));
        }

        String traceback = extractTail(content, markerIndex, maxChars);
        return ToolExecutionResult.success("Log source: %s%n%n%s".formatted(toolPolicy.display(logPath), traceback));
    }

    /** Keeps the most relevant tail section around the last exception marker. */
    private String extractTail(String content, int markerIndex, int maxChars) {
        int limit = Math.max(1000, maxChars);
        int start = Math.max(0, markerIndex - limit / 3);
        int end = Math.min(content.length(), markerIndex + limit);
        String window = content.substring(start, end);
        int relativeMarker = markerIndex - start;
        int lineStart = window.lastIndexOf('\n', relativeMarker);
        if (lineStart >= 0) {
            return window.substring(lineStart + 1).trim();
        }
        return window.trim();
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

    /** Finds the latest marker that indicates an actual error or Java stack trace. */
    private int lastExceptionMarker(String content) {
        int markerIndex = -1;
        for (String marker : new String[] {" ERROR ", "Exception", "java.lang.", "Caused by:"}) {
            markerIndex = Math.max(markerIndex, content.lastIndexOf(marker));
        }
        return markerIndex;
    }
}
