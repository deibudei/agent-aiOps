package org.example.agentaiops.repair.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import org.example.agentaiops.repair.agentic.AgenticRepairRunner;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairRunResponse;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.RepairTiming;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.tool.RepairWorkspaceContext;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairWorkflowService {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private final RepairEventHub eventHub;
    private final AgenticRepairRunner agenticRepairRunner;
    private final RepairRecordTools repairRecordTools;
    private final RepairWorkspaceContext workspaceContext;
    private final Executor repairTaskExecutor;

    /** Wires the Agentic repair workflow entrypoint. */
    public RepairWorkflowService(
            RepairEventHub eventHub,
            AgenticRepairRunner agenticRepairRunner,
            RepairRecordTools repairRecordTools,
            RepairWorkspaceContext workspaceContext,
            @Qualifier("repairTaskExecutor") Executor repairTaskExecutor) {
        this.eventHub = eventHub;
        this.agenticRepairRunner = agenticRepairRunner;
        this.repairRecordTools = repairRecordTools;
        this.workspaceContext = workspaceContext;
        this.repairTaskExecutor = repairTaskExecutor;
    }

    /** Accepts an API request and runs the Agentic repair workflow on the repair executor. */
    public RepairRunResponse startAsync(String requestedSessionId) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString()
                : validateSessionId(requestedSessionId);
        eventHub.publish(sessionId, RepairStage.DETECTING, "Repair workflow accepted");
        repairTaskExecutor.execute(() -> run(sessionId));
        return new RepairRunResponse(sessionId, "started", "/api/repair/stream/" + sessionId);
    }

    /** Accepts an API request and runs the repair workflow in an isolated workspace. */
    public RepairRunResponse startAsync(String requestedSessionId, String workspaceRoot) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString()
                : validateSessionId(requestedSessionId);
        Path activeWorkspace = validateWorkspaceRoot(workspaceRoot);
        eventHub.publish(sessionId, RepairStage.DETECTING, "Repair workflow accepted",
                Map.of("workspaceRoot", activeWorkspace.toString()));
        repairTaskExecutor.execute(() -> workspaceContext.runWithWorkspace(activeWorkspace, () -> run(sessionId)));
        return new RepairRunResponse(sessionId, "started", "/api/repair/stream/" + sessionId);
    }

    /** Runs one LangChain4j-backed repair DAG, failing fast when model config is unavailable. */
    private void run(String sessionId) {
        Instant startedAt = Instant.now();
        try {
            if (!agenticRepairRunner.available()) {
                publishErrorOutcome(
                        sessionId,
                        startedAt,
                        "Repair requires REPAIR_LLM_ENABLED=true and a configured provider API key",
                        "Repair LLM is unavailable");
                return;
            }
            eventHub.publish(sessionId, RepairStage.EXECUTING,
                    "Deterministic repair DAG is enabled");
            agenticRepairRunner.run(sessionId, startedAt);
        } catch (Exception e) {
            publishErrorOutcome(
                    sessionId,
                    startedAt,
                    "Repair workflow failed: " + describe(e),
                    describe(e));
        }
    }

    private void publishErrorOutcome(String sessionId, Instant startedAt, String message, String outcomeReason) {
        eventHub.publish(sessionId, RepairStage.ERROR, message,
                Map.of(
                        "outcome", RepairOutcome.ERROR,
                        "outcomeReason", outcomeReason));
        writeErrorRecord(sessionId, startedAt, outcomeReason);
    }

    private void writeErrorRecord(String sessionId, Instant startedAt, String outcomeReason) {
        Instant completedAt = Instant.now();
        RepairRecord record = new RepairRecord(
                1,
                sessionId,
                startedAt,
                completedAt,
                RepairOutcome.ERROR,
                outcomeReason,
                null,
                "",
                null,
                List.of(),
                null,
                null,
                "",
                null,
                null,
                null,
                null,
                null,
                new RepairReflection(
                        outcomeReason,
                        "The workflow ended before a complete repair record could be produced.",
                        "No code repair was committed because the run ended with a system error.",
                        "Target-service tests did not run to completion in this error path.",
                        "Keep configuration and typed model-output failures visible in repair records.",
                        List.of(
                                "Check LLM provider configuration and credentials.",
                                "Inspect the ERROR SSE event details before rerunning the repair.")),
                new RepairTiming(
                        startedAt,
                        completedAt,
                        Math.max(0, Duration.between(startedAt, completedAt).toMillis()),
                        List.of(),
                        List.of()));
        try {
            ToolExecutionResult write = repairRecordTools.writeRecord(record);
            if (!write.success()) {
                eventHub.publish(sessionId, RepairStage.ERROR,
                        "Failed to write repair error record: " + write.error(),
                        Map.of("outcome", RepairOutcome.ERROR, "outcomeReason", outcomeReason));
            }
        } catch (RuntimeException e) {
            eventHub.publish(sessionId, RepairStage.ERROR,
                    "Failed to write repair error record: " + e.getMessage(),
                    Map.of("outcome", RepairOutcome.ERROR, "outcomeReason", outcomeReason));
        }
    }

    private String validateSessionId(String sessionId) {
        String trimmed = sessionId.trim();
        if (!SESSION_ID_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sessionId must match [A-Za-z0-9._-]{1,80}");
        }
        return trimmed;
    }

    private Path validateWorkspaceRoot(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceRoot is required");
        }
        return Paths.get(workspaceRoot).toAbsolutePath().normalize();
    }

    /** Includes exception type when an exception has no message. */
    private String describe(Exception exception) {
        Throwable root = unwrap(exception);
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (true) {
            if (current instanceof UndeclaredThrowableException undeclared
                    && undeclared.getUndeclaredThrowable() != null) {
                current = undeclared.getUndeclaredThrowable();
                continue;
            }
            if (current instanceof InvocationTargetException invocation
                    && invocation.getTargetException() != null) {
                current = invocation.getTargetException();
                continue;
            }
            return current;
        }
    }
}
