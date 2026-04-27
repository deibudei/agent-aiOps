package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
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

    @Agent(name = "collectEvidence", description = "Collect traceback, tests, and source snippets",
            outputKey = "evidence")
    public String collectEvidence() {
        eventHub.publish(state.sessionId, RepairStage.DETECTING,
                "Agentic EvidenceAgent collecting traceback, tests, and source evidence");
        state.evidenceBundle = evidenceAgent.collect();
        state.evidence = AgenticEvidenceFormatter.compactEvidence(state.evidenceBundle);
        state.step("EvidenceAgent", "collect", state.evidenceBundle.summary(), true);
        eventHub.publish(state.sessionId, RepairStage.DETECTING, state.evidenceBundle.summary(),
                Map.of("evidence", state.evidenceBundle));
        return state.evidence;
    }
}
