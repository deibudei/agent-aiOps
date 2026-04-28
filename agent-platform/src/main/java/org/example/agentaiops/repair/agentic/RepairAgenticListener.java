package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import java.util.Map;
import java.util.Set;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Bridges LangChain4j Agentic events into the repair SSE stream. */
public final class RepairAgenticListener implements AgentListener {

    private static final Set<String> TIMING_EXCLUDED_AGENTS = Set.of("repairSupervisor", "writeRepairRecord");

    private final AgenticRepairState state;
    private final String sessionId;
    private final RepairEventHub eventHub;
    private final Map<String, String> roleByAgent;
    private final Map<String, String> modelByAgent;

    public RepairAgenticListener(
            AgenticRepairState state,
            RepairEventHub eventHub,
            Map<String, String> roleByAgent,
            Map<String, String> modelByAgent) {
        this.state = state;
        this.sessionId = state.sessionId;
        this.eventHub = eventHub;
        this.roleByAgent = roleByAgent;
        this.modelByAgent = modelByAgent;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        if (shouldTime(request.agentName())) {
            state.beginTiming(request.agentName());
        }
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic invoking " + request.agentName());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        RepairModelUsage modelUsage = state.recordModelUsage(
                response.agentName(),
                roleByAgent.get(response.agentName()),
                modelByAgent.get(response.agentName()),
                response.chatResponse());
        if (shouldTime(response.agentName())) {
            state.endTiming(response.agentName(), true, "Agent completed");
        }
        if (modelUsage == null) {
            eventHub.publish(sessionId, RepairStage.EXECUTING,
                    "Agentic completed " + response.agentName());
            return;
        }
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic completed " + response.agentName(),
                Map.of("modelUsage", modelUsage));
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        if (shouldTime(error.agentName())) {
            state.endTiming(error.agentName(), false, errorMessage(error));
        }
        eventHub.publish(sessionId, RepairStage.ERROR,
                "Agentic agent failed: " + error.agentName() + ": " + errorMessage(error));
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution toolExecution) {
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic tool call: "
                        + AgenticEvidenceFormatter.trim(String.valueOf(toolExecution.toolExecution()), 240));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution toolExecution) {
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic tool completed: "
                        + AgenticEvidenceFormatter.trim(String.valueOf(toolExecution.toolExecution()), 240));
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    private boolean shouldTime(String agentName) {
        return agentName != null && !TIMING_EXCLUDED_AGENTS.contains(agentName);
    }

    private String errorMessage(AgentInvocationError error) {
        Throwable throwable = error.error();
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
