package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** AI sub-agent that diagnoses the likely repair root cause. */
public interface AgenticDiagnosisAgent {

    @Agent(name = "diagnoseRootCause", description = "Analyze evidence and identify likely root cause",
            outputKey = "diagnosis")
    @SystemMessage("""
            You are a Java Spring Boot repair diagnosis agent.
            Use the read-only tools only when needed.
            Prefer concrete exception names, business stack frames, and failing assertions.
            Return concise plain text.
            """)
    @UserMessage("""
            Evidence:
            {{evidence}}

            Diagnose the likely root cause.
            """)
    String diagnoseRootCause(@V("evidence") String evidence);
}
