package org.example.agentaiops.llm;

import java.util.List;
import java.util.Optional;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.SourceSnippet;
import org.springframework.stereotype.Component;

@Component
public class LangChainPatchPlanner {

    private final RepairChatModelProvider chatModelProvider;
    private final StructuredJsonParser jsonParser;

    /** Injects the LangChain4j model provider and strict JSON parser. */
    public LangChainPatchPlanner(RepairChatModelProvider chatModelProvider, StructuredJsonParser jsonParser) {
        this.chatModelProvider = chatModelProvider;
        this.jsonParser = jsonParser;
    }

    /** Reports whether LLM patch planning can run in the current environment. */
    public boolean enabled() {
        return chatModelProvider.available();
    }

    /** Asks the model for patch operations without applying them to disk. */
    public Optional<PatchProposal> propose(RepairPlan plan, List<SourceSnippet> snippets) {
        if (!enabled()) {
            return Optional.empty();
        }
        String response = chatModelProvider.chatModel().chat(patchPrompt(plan, snippets));
        return jsonParser.parse(response, PatchProposal.class)
                .map(proposal -> new PatchProposal(
                        proposal.repairTarget(),
                        proposal.rootCause(),
                        proposal.operations(),
                        proposal.testsToRun(),
                        true,
                        response));
    }

    /** Builds the patch prompt with exact oldText/newText requirements. */
    private String patchPrompt(RepairPlan plan, List<SourceSnippet> snippets) {
        return """
                You are a Java Spring Boot repair executor.
                Generate a minimal patch proposal for the repair plan and source snippets.

                Return only strict JSON matching this schema:
                {
                  "repairTarget": "same target as the plan",
                  "rootCause": "specific root cause",
                  "operations": [
                    {
                      "filePath": "target-service/src/main/java/...",
                      "oldText": "exact text currently present in the file",
                      "newText": "replacement text",
                      "reason": "why this change is needed"
                    }
                  ],
                  "testsToRun": ["mvn -pl target-service test"],
                  "modelGenerated": true,
                  "rawModelOutput": ""
                }

                Constraints:
                - Only propose filePath values under target-service/src/main or target-service/src/test.
                - oldText must be copied exactly from the supplied source snippets.
                - Do not rewrite whole files unless the file is tiny.
                - Do not modify agent-platform, root configs, secrets, scripts, or build files.
                - If no safe patch can be generated, return operations as an empty array.

                Repair plan:
                %s

                Source snippets:
                %s
                """.formatted(plan, formatSnippets(snippets));
    }

    /** Formats selected source snippets so the model can quote exact replacement text. */
    private String formatSnippets(List<SourceSnippet> snippets) {
        StringBuilder builder = new StringBuilder();
        for (SourceSnippet snippet : snippets) {
            builder.append("FILE: ").append(snippet.path()).append(System.lineSeparator());
            builder.append(snippet.content()).append(System.lineSeparator()).append(System.lineSeparator());
        }
        return builder.toString();
    }
}
