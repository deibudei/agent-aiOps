package org.example.agentaiops.repair.agentic.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.example.agentaiops.repair.model.DiagnosisResult;

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
            - Return only the structured DiagnosisResult object; no markdown, comments, or prose.

            DiagnosisResult shape:
            {
              "failureSignal": "exception type or failing assertion",
              "primaryEvidence": "first application frame, failing test, or route",
              "rootCause": "specific root cause",
              "violatedContract": "input boundary or API/service contract",
              "suspectedFiles": ["target-service/src/main/java/..."],
              "confidence": 0.0
            }

            Few-shot example 1:
            Evidence: java.lang.ArithmeticException: / by zero at
            OrderService.calculateUnitPrice(OrderService.java:10), called by OrderController.quote.
            DiagnosisResult:
            {
              "failureSignal": "java.lang.ArithmeticException: / by zero",
              "primaryEvidence": "OrderService.calculateUnitPrice divides totalCents by quantity before validation.",
              "rootCause": "quantity=0 reaches OrderService.calculateUnitPrice and triggers integer division by zero.",
              "violatedContract": "quantity must be positive before unit-price arithmetic.",
              "suspectedFiles": [
                "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                "target-service/src/test/java/com/example/targetservice/service/OrderServiceTest.java"
              ],
              "confidence": 0.96
            }

            Few-shot example 2:
            Evidence: OrderControllerTest expects GET /api/orders/quote but receives 404.
            DiagnosisResult:
            {
              "failureSignal": "MockMvc 404 for GET /api/orders/quote",
              "primaryEvidence": "OrderController route mapping does not match the public quote endpoint expected by the controller test.",
              "rootCause": "The quote endpoint mapping drifted from /api/orders/quote, so the request is not routed to OrderController.quote.",
              "violatedContract": "The public quote endpoint must remain GET /api/orders/quote.",
              "suspectedFiles": [
                "target-service/src/main/java/com/example/targetservice/controller/OrderController.java",
                "target-service/src/test/java/com/example/targetservice/controller/OrderControllerTest.java"
              ],
              "confidence": 0.9
            }
            """)
    @UserMessage("""
            Evidence:
            {{evidence}}

            Diagnose the likely root cause.
            """)
    DiagnosisResult diagnoseRootCause(@V("evidence") String evidence);
}
