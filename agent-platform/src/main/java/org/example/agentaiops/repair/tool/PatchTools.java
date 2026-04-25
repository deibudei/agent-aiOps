package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.agentaiops.repair.model.PatchResult;
import org.springframework.stereotype.Component;

@Component
public class PatchTools {

    private final ToolPolicy toolPolicy;

    public PatchTools(ToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

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
