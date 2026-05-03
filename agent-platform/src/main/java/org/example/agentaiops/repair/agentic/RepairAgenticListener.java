package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import java.util.LinkedHashMap;
import java.util.Map;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Bridges LangChain4j Agentic events into the repair SSE stream. */
public final class RepairAgenticListener implements AgentListener {

    private final String sessionId;
    private final RepairEventHub eventHub;
    private final Map<String, String> roleByAgent;
    private final Map<String, String> modelByAgent;

    public RepairAgenticListener(
            AgenticRepairState state,
            RepairEventHub eventHub,
            Map<String, String> roleByAgent,
            Map<String, String> modelByAgent) {
        this.sessionId = state.sessionId;
        this.eventHub = eventHub;
        this.roleByAgent = Map.copyOf(roleByAgent);
        this.modelByAgent = Map.copyOf(modelByAgent);
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "AI agent started: " + request.agentName(),
                agentDetails("agent_started", request.agentName(), "running"));
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "AI agent completed: " + response.agentName(),
                agentDetails("agent_completed", response.agentName(), "completed"));
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        eventHub.publish(sessionId, RepairStage.ERROR,
                "Agentic agent failed: " + error.agentName() + ": " + errorMessage(error));
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution toolExecution) {
        String summary = AgenticEvidenceFormatter.trim(String.valueOf(toolExecution.toolExecution()), 240);
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic tool started: " + summary,
                toolDetails("agent_tool_started", "AgentTool", summary, "running", true));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution toolExecution) {
        String summary = AgenticEvidenceFormatter.trim(String.valueOf(toolExecution.toolExecution()), 240);
        eventHub.publish(sessionId, RepairStage.EXECUTING,
                "Agentic tool completed: " + summary,
                toolDetails("agent_tool_completed", "AgentTool", summary, "completed", true));
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

    private Map<String, Object> agentDetails(String eventType, String agentName, String status) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", eventType);
        details.put("agentName", agentName);
        details.put("role", roleByAgent.getOrDefault(agentName, "AGENT"));
        details.put("model", modelByAgent.getOrDefault(agentName, ""));
        details.put("status", status);
        return details;
    }

    private Map<String, Object> toolDetails(
            String eventType, String toolName, String summary, String status, boolean success) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", eventType);
        details.put("toolName", toolName);
        details.put("target", summary);
        details.put("summary", summary);
        details.put("status", status);
        details.put("success", success);
        details.put("source", "langchain4j-agent-listener");
        return details;
    }
}
