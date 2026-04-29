package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import java.util.Map;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Bridges LangChain4j Agentic events into the repair SSE stream. */
public final class RepairAgenticListener implements AgentListener {

    private final String sessionId;
    private final RepairEventHub eventHub;

    public RepairAgenticListener(
            AgenticRepairState state,
            RepairEventHub eventHub,
            Map<String, String> roleByAgent,
            Map<String, String> modelByAgent) {
        this.sessionId = state.sessionId;
        this.eventHub = eventHub;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic invoking " + request.agentName());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic completed " + response.agentName());
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
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

    private String errorMessage(AgentInvocationError error) {
        Throwable throwable = error.error();
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
