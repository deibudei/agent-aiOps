package org.example.agentaiops.repair.model;

import dev.langchain4j.model.output.structured.Description;

/** Describes one safe exact-text replacement requested by the model. */
@Description("One exact-text replacement to apply in a whitelisted target-service file.")
public record PatchOperation(
        @Description("Repo-relative file path under target-service/src/main or target-service/src/test.")
        String filePath,
        @Description("Exact text currently present in the file, including whitespace and line breaks.")
        String oldText,
        @Description("Replacement text to write in place of oldText.")
        String newText,
        @Description("Short Simplified Chinese explanation of why this replacement fixes the root cause.")
        String reason) {
}
