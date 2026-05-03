package org.example.agentaiops.repair.agentic.operators;

import java.util.LinkedHashMap;
import java.util.Map;
import org.example.agentaiops.repair.agent.EvidenceAgent;
import org.example.agentaiops.repair.agentic.AgenticEvidenceFormatter;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.service.RepairEventHub;

/** Collects evidence before AI diagnosis and planning. */
public final class EvidenceOperator {

    private final AgenticRepairState state;
    private final EvidenceAgent evidenceAgent;
    private final RepairEventHub eventHub;

    public EvidenceOperator(AgenticRepairState state, EvidenceAgent evidenceAgent, RepairEventHub eventHub) {
        this.state = state;
        this.evidenceAgent = evidenceAgent;
        this.eventHub = eventHub;
    }

    public String collectEvidence() {
        eventHub.publish(state.sessionId, RepairStage.DETECTING, "ReadLog started: target-service/logs",
                toolDetails(
                        "tool_started",
                        "ReadLog",
                        "target-service/logs",
                        "running",
                        true,
                        "Reading latest traceback evidence"));
        eventHub.publish(state.sessionId, RepairStage.DETECTING,
                "EvidenceAgent collecting traceback, tests, and source evidence");
        state.evidenceBundle = evidenceAgent.collect();
        state.evidence = AgenticEvidenceFormatter.compactEvidence(state.evidenceBundle);
        state.step("EvidenceAgent", "collect", state.evidenceBundle.summary(), true);
        eventHub.publish(state.sessionId, RepairStage.DETECTING, "ReadLog completed: target-service/logs",
                toolDetails(
                        "tool_completed",
                        "ReadLog",
                        "target-service/logs",
                        "completed",
                        true,
                        state.evidenceBundle.summary()));
        eventHub.publish(state.sessionId, RepairStage.DETECTING, state.evidenceBundle.summary(),
                Map.of("evidence", state.evidenceBundle));
        return state.evidence;
    }

    private Map<String, Object> toolDetails(
            String eventType,
            String toolName,
            String target,
            String status,
            boolean success,
            String summary) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", eventType);
        details.put("toolName", toolName);
        details.put("target", target);
        details.put("status", status);
        details.put("success", success);
        details.put("summary", summary);
        details.put("source", "evidence-operator");
        return details;
    }
}
