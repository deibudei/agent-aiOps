package org.example.agentaiops.repair.agentic.operators;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import org.example.agentaiops.repair.agentic.AgenticEvidenceFormatter;
import org.example.agentaiops.repair.agentic.AgenticRepairState;

/** Prepares bounded source context for patch proposal generation. */
public final class SourceContextOperator {

    private final AgenticRepairState state;

    public SourceContextOperator(AgenticRepairState state) {
        this.state = state;
    }

    @Agent(name = "prepareSourceContext", description = "Prepare selected source snippets for patch generation",
            outputKey = "sourceContext")
    public String prepareSourceContext(@V("evidence") String evidence) {
        state.sourceContext = AgenticEvidenceFormatter.sourceContext(state.evidenceBundle);
        state.step("SourceContext", "sourceSnippets", "chars=" + state.sourceContext.length(), true);
        return state.sourceContext;
    }
}
