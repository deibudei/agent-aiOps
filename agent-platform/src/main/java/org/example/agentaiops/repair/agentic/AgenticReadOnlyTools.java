package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.tool.ReadCodeTools;
import org.example.agentaiops.repair.tool.ReadLogTools;

/** Read-only tools exposed to AI sub-agents. */
public final class AgenticReadOnlyTools {

    private final ReadLogTools readLogTools;
    private final ReadCodeTools readCodeTools;
    private final boolean fileReadCacheEnabled;
    private final Map<String, CacheEntry> fileCache = new ConcurrentHashMap<>();

    public AgenticReadOnlyTools(ReadLogTools readLogTools, ReadCodeTools readCodeTools) {
        this(readLogTools, readCodeTools, true);
    }

    public AgenticReadOnlyTools(ReadLogTools readLogTools, ReadCodeTools readCodeTools, boolean fileReadCacheEnabled) {
        this.readLogTools = readLogTools;
        this.readCodeTools = readCodeTools;
        this.fileReadCacheEnabled = fileReadCacheEnabled;
    }

    @Tool("Read the latest target-service traceback from logs.")
    public String readLatestTraceback(@P("Maximum characters to return") int maxChars) {
        ToolExecutionResult result = readLogTools.readLatestTraceback(
                Math.min(maxChars, AgenticEvidenceFormatter.AGENTIC_TRACEBACK_CHARS));
        return result.success()
                ? AgenticEvidenceFormatter.trim(result.output(), AgenticEvidenceFormatter.AGENTIC_TRACEBACK_CHARS)
                : "ERROR: " + result.error();
    }

    @Tool("Read one target-service source or log file from the whitelist.")
    public String readFile(@P("Repository-relative target-service path") String path) {
        if (!fileReadCacheEnabled) {
            ToolExecutionResult result = readCodeTools.readFile(path);
            return result.success()
                    ? AgenticEvidenceFormatter.trim(result.output(), AgenticEvidenceFormatter.AGENTIC_FILE_CHARS)
                    : "ERROR: " + result.error();
        }

        ToolExecutionResult mtimeResult = readCodeTools.lastModifiedMillis(path);
        if (!mtimeResult.success()) {
            return "ERROR: " + mtimeResult.error();
        }
        long mtime;
        try {
            mtime = Long.parseLong(mtimeResult.output().trim());
        } catch (NumberFormatException e) {
            return "ERROR: invalid mtime for " + path;
        }

        CacheEntry cached = fileCache.get(path);
        if (cached != null && cached.lastModifiedMillis == mtime) {
            return cached.content;
        }

        ToolExecutionResult readResult = readCodeTools.readFile(path);
        if (!readResult.success()) {
            return "ERROR: " + readResult.error();
        }
        String trimmed = AgenticEvidenceFormatter.trim(readResult.output(), AgenticEvidenceFormatter.AGENTIC_FILE_CHARS);
        fileCache.put(path, new CacheEntry(mtime, trimmed));
        return trimmed;
    }

    @Tool("Search target-service Java source files for a literal string.")
    public String searchCode(@P("Literal query string") String query) {
        ToolExecutionResult result = readCodeTools.searchCode(query);
        return result.success()
                ? AgenticEvidenceFormatter.trim(result.output(), AgenticEvidenceFormatter.AGENTIC_SEARCH_CHARS)
                : "ERROR: " + result.error();
    }

    private record CacheEntry(long lastModifiedMillis, String content) {}
}
