package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        Map<Path, String> updatedFiles = new LinkedHashMap<>();
        List<PatchResult> results = new ArrayList<>();
        for (PatchOperation operation : proposal.operations()) {
            PatchResult result = preflightReplace(operation, updatedFiles);
            results.add(result);
            if (!result.success()) {
                return new PatchApplicationResult(false, results, result.message());
            }
        }

        for (Map.Entry<Path, String> entry : updatedFiles.entrySet()) {
            try {
                Files.writeString(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                return new PatchApplicationResult(false, results, e.getMessage());
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

            int occurrences = countOccurrences(content, normalizedExpected);
            if (occurrences == 0) {
                return new PatchResult(false, toolPolicy.display(resolved), "Expected text not found");
            }
            if (occurrences > 1) {
                return new PatchResult(false, toolPolicy.display(resolved), "Expected text matched more than once");
            }

            String updated = replaceOnce(content, normalizedExpected, normalizedReplacement);
            Files.writeString(resolved, updated);
            return new PatchResult(true, toolPolicy.display(resolved), "Patch applied");
        } catch (IOException | IllegalArgumentException e) {
            return new PatchResult(false, path, e.getMessage());
        }
    }

    private PatchResult preflightReplace(PatchOperation operation, Map<Path, String> updatedFiles) {
        try {
            Path resolved = toolPolicy.resolveForWrite(operation.filePath());
            String content = updatedFiles.computeIfAbsent(resolved,
                    path -> readNormalized(path, operation.filePath()));
            if (content == null) {
                return new PatchResult(false, operation.filePath(), "Unable to read target file");
            }

            String normalizedExpected = operation.oldText().replace("\r\n", "\n");
            String normalizedReplacement = operation.newText().replace("\r\n", "\n");
            int occurrences = countOccurrences(content, normalizedExpected);
            if (occurrences == 0) {
                return new PatchResult(false, toolPolicy.display(resolved), "Expected text not found");
            }
            if (occurrences > 1) {
                return new PatchResult(false, toolPolicy.display(resolved), "Expected text matched more than once");
            }

            updatedFiles.put(resolved, replaceOnce(content, normalizedExpected, normalizedReplacement));
            return new PatchResult(true, toolPolicy.display(resolved), "Patch preflight passed");
        } catch (IllegalArgumentException e) {
            return new PatchResult(false, operation.filePath(), e.getMessage());
        }
    }

    private String readNormalized(Path path, String displayPath) {
        try {
            return Files.readString(path).replace("\r\n", "\n");
        } catch (IOException e) {
            return null;
        }
    }

    private int countOccurrences(String content, String expected) {
        if (expected == null || expected.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(expected, index)) >= 0) {
            count++;
            index += expected.length();
        }
        return count;
    }

    private String replaceOnce(String content, String expected, String replacement) {
        int index = content.indexOf(expected);
        return content.substring(0, index) + replacement + content.substring(index + expected.length());
    }
}
