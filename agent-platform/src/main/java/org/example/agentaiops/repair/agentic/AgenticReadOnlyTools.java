package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.ReadCodeTools;
import org.example.agentaiops.repair.tool.ReadLogTools;

/** Read-only tools exposed to AI sub-agents. */
public final class AgenticReadOnlyTools {

    private final ReadLogTools readLogTools;
    private final ReadCodeTools readCodeTools;
    private final boolean fileReadCacheEnabled;
    private final String sessionId;
    private final RepairEventHub eventHub;
    private final RepairStage codeStage;
    private final Map<String, CacheEntry> fileCache = new ConcurrentHashMap<>();

    public AgenticReadOnlyTools(ReadLogTools readLogTools, ReadCodeTools readCodeTools) {
        this(readLogTools, readCodeTools, true);
    }

    public AgenticReadOnlyTools(ReadLogTools readLogTools, ReadCodeTools readCodeTools, boolean fileReadCacheEnabled) {
        this(readLogTools, readCodeTools, fileReadCacheEnabled, "", null);
    }

    public AgenticReadOnlyTools(
            ReadLogTools readLogTools,
            ReadCodeTools readCodeTools,
            boolean fileReadCacheEnabled,
            String sessionId,
            RepairEventHub eventHub) {
        this(readLogTools, readCodeTools, fileReadCacheEnabled, sessionId, eventHub, RepairStage.PLANNING);
    }

    public AgenticReadOnlyTools(
            ReadLogTools readLogTools,
            ReadCodeTools readCodeTools,
            boolean fileReadCacheEnabled,
            String sessionId,
            RepairEventHub eventHub,
            RepairStage codeStage) {
        this.readLogTools = readLogTools;
        this.readCodeTools = readCodeTools;
        this.fileReadCacheEnabled = fileReadCacheEnabled;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.eventHub = eventHub;
        this.codeStage = codeStage == null ? RepairStage.PLANNING : codeStage;
    }

    @Tool("Read the latest target-service traceback from logs.")
    public String readLatestTraceback(@P("Maximum characters to return") int maxChars) {
        if (readLogTools == null) {
            return "ReadLog is not available in this agent context.";
        }
        String target = "target-service/logs";
        publishTool(
                RepairStage.DETECTING,
                "tool_started",
                "ReadLog",
                target,
                "running",
                true,
                "Reading latest traceback log");
        ToolExecutionResult result = readLogTools.readLatestTraceback(
                Math.min(maxChars, AgenticEvidenceFormatter.AGENTIC_TRACEBACK_CHARS));
        String output = result.success()
                ? AgenticEvidenceFormatter.trim(result.output(), AgenticEvidenceFormatter.AGENTIC_TRACEBACK_CHARS)
                : "ERROR: " + result.error();
        publishTool(
                RepairStage.DETECTING,
                "tool_completed",
                "ReadLog",
                target,
                result.success() ? "completed" : "failed",
                result.success(),
                result.success() ? "Latest traceback log loaded" : result.error());
        return output;
    }

    @Tool("Read one target-service source or log file from the whitelist. Prefer readCode for source files and readLog for log files.")
    public String readFile(@P("Repository-relative target-service path") String path) {
        if (isLogPath(path) && readLogTools == null) {
            return "ReadLog is not available in this agent context. Use readCode for source files.";
        }
        return isLogPath(path) ? readLog(path) : readCode(path);
    }

    @Tool("Read one target-service log file from the log whitelist.")
    public String readLog(@P("Repository-relative target-service log path") String path) {
        if (readLogTools == null) {
            return "ReadLog is not available in this agent context.";
        }
        return readWhitelistedFile(path, "ReadLog", RepairStage.DETECTING);
    }

    @Tool("Read one target-service source or test file from the code whitelist.")
    public String readCode(@P("Repository-relative target-service source path") String path) {
        return readWhitelistedFile(path, "ReadCode", codeStage);
    }

    private String readWhitelistedFile(String path, String toolName, RepairStage stage) {
        publishTool(stage, "tool_started", toolName, path, "running", true, "Reading " + path);
        if (!fileReadCacheEnabled) {
            ToolExecutionResult result = readCodeTools.readFile(path);
            String output = result.success()
                    ? AgenticEvidenceFormatter.trim(result.output(), AgenticEvidenceFormatter.AGENTIC_FILE_CHARS)
                    : "ERROR: " + result.error();
            publishTool(
                    stage,
                    "tool_completed",
                    toolName,
                    path,
                    result.success() ? "completed" : "failed",
                    result.success(),
                    result.success() ? "File loaded" : result.error());
            return output;
        }

        ToolExecutionResult mtimeResult = readCodeTools.lastModifiedMillis(path);
        if (!mtimeResult.success()) {
            publishTool(stage, "tool_completed", toolName, path, "failed", false, mtimeResult.error());
            return "ERROR: " + mtimeResult.error();
        }
        long mtime;
        try {
            mtime = Long.parseLong(mtimeResult.output().trim());
        } catch (NumberFormatException e) {
            publishTool(stage, "tool_completed", toolName, path, "failed", false, "Invalid file mtime");
            return "ERROR: invalid mtime for " + path;
        }

        CacheEntry cached = fileCache.get(path);
        if (cached != null && cached.lastModifiedMillis == mtime) {
            publishTool(stage, "tool_completed", toolName, path, "cached", true, "File loaded from read cache");
            return cached.content;
        }

        ToolExecutionResult readResult = readCodeTools.readFile(path);
        if (!readResult.success()) {
            publishTool(stage, "tool_completed", toolName, path, "failed", false, readResult.error());
            return "ERROR: " + readResult.error();
        }
        String trimmed = AgenticEvidenceFormatter.trim(readResult.output(), AgenticEvidenceFormatter.AGENTIC_FILE_CHARS);
        fileCache.put(path, new CacheEntry(mtime, trimmed));
        publishTool(stage, "tool_completed", toolName, path, "completed", true, "File loaded");
        return trimmed;
    }

    @Tool("Search target-service Java source files for a literal string.")
    public String searchCode(@P("Literal query string") String query) {
        publishTool(
                codeStage,
                "tool_started",
                "SearchCode",
                query,
                "running",
                true,
                "Searching target-service Java sources");
        ToolExecutionResult result = readCodeTools.searchCode(query);
        String output = result.success()
                ? AgenticEvidenceFormatter.trim(result.output(), AgenticEvidenceFormatter.AGENTIC_SEARCH_CHARS)
                : "ERROR: " + result.error();
        publishTool(
                codeStage,
                "tool_completed",
                "SearchCode",
                query,
                result.success() ? "completed" : "failed",
                result.success(),
                result.success() ? "Search completed" : result.error());
        return output;
    }

    private void publishTool(
            RepairStage stage,
            String eventType,
            String toolName,
            String target,
            String status,
            boolean success,
            String summary) {
        if (eventHub == null || sessionId.isBlank()) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", eventType);
        details.put("toolName", toolName);
        details.put("target", target == null ? "" : target);
        details.put("status", status);
        details.put("success", success);
        details.put("summary", summary == null ? "" : AgenticEvidenceFormatter.trim(summary, 240));
        details.put("source", "agentic-read-only-tools");
        eventHub.publish(
                sessionId,
                stage,
                toolName + " " + status + ": " + (target == null || target.isBlank() ? "target-service" : target),
                details);
    }

    private boolean isLogPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/').toLowerCase();
        return normalized.contains("/logs/") || normalized.endsWith(".log") || normalized.endsWith(".trace");
    }

    private record CacheEntry(long lastModifiedMillis, String content) {}
}
