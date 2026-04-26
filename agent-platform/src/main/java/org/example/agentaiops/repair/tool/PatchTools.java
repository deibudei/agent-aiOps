package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.springframework.stereotype.Component;

@Component
public class PatchTools {

    private final ToolPolicy toolPolicy;

    /** Keeps patch writes behind the shared path policy. */
    public PatchTools(ToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

    /** Applies every operation proposed by the LLM after path and text checks. */
    public PatchApplicationResult applyProposal(PatchProposal proposal) {
        if (proposal == null || proposal.operations() == null || proposal.operations().isEmpty()) {
            return new PatchApplicationResult(false, List.of(), "Patch proposal has no operations");
        }

        List<PatchResult> results = new ArrayList<>();
        for (PatchOperation operation : proposal.operations()) {
            PatchResult result = replaceInFile(operation.filePath(), operation.oldText(), operation.newText());
            results.add(result);
            if (!result.success()) {
                return new PatchApplicationResult(false, results, result.message());
            }
        }
        return new PatchApplicationResult(true, results, "Patch proposal applied");
    }

    /** Replaces exact text in a whitelisted target-service file. */
    public PatchResult replaceInFile(String path, String expected, String replacement) {
        try {
            Path resolved = toolPolicy.resolveForWrite(path);
            String content = Files.readString(resolved).replace("\r\n", "\n");
            String normalizedExpected = expected.replace("\r\n", "\n");
            String normalizedReplacement = replacement.replace("\r\n", "\n");

            if (!content.contains(normalizedExpected)) {
                return new PatchResult(false, toolPolicy.display(resolved), "Expected text not found");
            }

            String updated = content.replace(normalizedExpected, normalizedReplacement);
            Files.writeString(resolved, updated);
            return new PatchResult(true, toolPolicy.display(resolved), "Patch applied");
        } catch (IOException | IllegalArgumentException e) {
            return new PatchResult(false, path, e.getMessage());
        }
    }
}
