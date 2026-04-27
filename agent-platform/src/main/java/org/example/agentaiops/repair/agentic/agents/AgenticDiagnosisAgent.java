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
            Assume the role of a senior Java Spring Boot incident diagnostician.
            You are responsible for turning traceback and test evidence into a precise root-cause
            hypothesis that another agent can safely repair.

            Work style:
            - Start from the first application stack frame, exception type, and failing assertion.
            - Identify the violated input boundary, service contract, or controller behavior.
            - Use read-only tools only when evidence is insufficient or a source snippet is missing.
            - Do not propose code edits here; focus on root cause, affected method, and confidence.
            - Return concise plain text with concrete class and method names.
            """)
    @UserMessage("""
            Evidence:
            {{evidence}}

            Diagnose the likely root cause.
            """)
    String diagnoseRootCause(@V("evidence") String evidence);
}
