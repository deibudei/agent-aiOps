package org.example.agentaiops.llm;

import java.util.List;
import java.util.Optional;
import org.example.agentaiops.repair.model.RepairPlan;
import org.springframework.stereotype.Component;

@Component
public class LangChainRepairPlanner {

    private final RepairChatModelProvider chatModelProvider;
    private final StructuredJsonParser jsonParser;

    /** Injects the LangChain4j model provider and strict JSON parser. */
    public LangChainRepairPlanner(RepairChatModelProvider chatModelProvider, StructuredJsonParser jsonParser) {
        this.chatModelProvider = chatModelProvider;
        this.jsonParser = jsonParser;
    }

    /** Reports whether LLM planning can run in the current environment. */
    public boolean enabled() {
        return chatModelProvider.available();
    }

    /** Asks the model for a structured repair plan and rejects non-JSON responses. */
    public Optional<RepairPlan> plan(String evidence, List<String> toolNames, String testCommand) {
        if (!enabled()) {
            return Optional.empty();
        }
        String response = chatModelProvider.chatModel().chat(planningPrompt(evidence, toolNames, testCommand));
        return jsonParser.parse(response, RepairPlan.class);
    }

    /** Builds the planning prompt with safety boundaries and the expected JSON schema. */
    private String planningPrompt(String evidence, List<String> toolNames, String testCommand) {
        return """
                You are a Java Spring Boot repair planning agent.
                Analyze the traceback, failing test output, and source context.

                Return only strict JSON matching this schema:
                {
                  "repairTarget": "short user-facing repair target",
                  "rootCauseHypothesis": "specific root cause",
                  "suspectedFiles": ["target-service/src/main/java/..."],
                  "steps": ["step 1", "step 2"],
                  "testCommand": "%s"
                }

                Constraints:
                - Only propose files under target-service/src/main or target-service/src/test.
                - Do not propose changes to agent-platform, root pom.xml, secrets, scripts, or configuration outside target-service.
                - Prefer the smallest source change plus a regression test when needed.
                - Available tools: %s

                Evidence:
                %s
                """.formatted(testCommand, String.join(", ", toolNames), evidence);
    }
}
