package org.example.agentaiops.repair.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.example.agentaiops.repair.agentic.AgenticRepairRunner;
import org.example.agentaiops.repair.model.RepairRunResponse;
import org.example.agentaiops.repair.model.RepairStage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RepairWorkflowService {

    private final RepairEventHub eventHub;
    private final AgenticRepairRunner agenticRepairRunner;
    private final Executor repairTaskExecutor;

    /** Wires the Agentic repair workflow entrypoint. */
    public RepairWorkflowService(
            RepairEventHub eventHub,
            AgenticRepairRunner agenticRepairRunner,
            @Qualifier("repairTaskExecutor") Executor repairTaskExecutor) {
        this.eventHub = eventHub;
        this.agenticRepairRunner = agenticRepairRunner;
        this.repairTaskExecutor = repairTaskExecutor;
    }

    /** Accepts an API request and runs the Agentic repair workflow on the repair executor. */
    public RepairRunResponse startAsync(String requestedSessionId) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedSessionId;
        eventHub.publish(sessionId, RepairStage.DETECTING, "Repair workflow accepted");
        repairTaskExecutor.execute(() -> run(sessionId));
        return new RepairRunResponse(sessionId, "started", "/api/repair/stream/" + sessionId);
    }

    /** Runs one LangChain4j-backed repair DAG, failing fast when model config is unavailable. */
    private void run(String sessionId) {
        Instant startedAt = Instant.now();
        try {
            if (!agenticRepairRunner.available()) {
                eventHub.publish(sessionId, RepairStage.ERROR,
                        "Repair requires REPAIR_LLM_ENABLED=true and a configured provider API key");
                return;
            }
            eventHub.publish(sessionId, RepairStage.EXECUTING,
                    "Deterministic repair DAG is enabled");
            agenticRepairRunner.run(sessionId, startedAt);
        } catch (Exception e) {
            eventHub.publish(sessionId, RepairStage.ERROR, "Repair workflow failed: " + describe(e));
        }
    }

    /** Includes exception type when an exception has no message. */
    private String describe(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }
}
