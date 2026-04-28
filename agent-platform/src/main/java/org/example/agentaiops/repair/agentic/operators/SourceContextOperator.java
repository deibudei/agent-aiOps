package org.example.agentaiops.repair.agentic.operators;

import org.example.agentaiops.repair.agentic.AgenticEvidenceFormatter;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.RepairPlan;

/** Prepares bounded source context for patch proposal generation. */
public final class SourceContextOperator {

    private final AgenticRepairState state;

    public SourceContextOperator(AgenticRepairState state) {
        this.state = state;
    }

    public String prepareSourceContext(String evidence, RepairPlan plan) {
        if (state.plan == null) {
            state.plan = plan;
        }
        state.sourceContext = AgenticEvidenceFormatter.sourceContext(state.evidenceBundle);
        state.step("SourceContext", "sourceSnippets", "chars=" + state.sourceContext.length(), true);
        return state.sourceContext;
    }
}
