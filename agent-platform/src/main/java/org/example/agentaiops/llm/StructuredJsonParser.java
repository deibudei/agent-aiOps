package org.example.agentaiops.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StructuredJsonParser {

    private final ObjectMapper objectMapper;

    /** Uses the shared Jackson mapper so model JSON follows the app's normal mapping rules. */
    public StructuredJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Extracts a JSON object from model output and parses it into the requested type. */
    public <T> Optional<T> parse(String rawOutput, Class<T> type) {
        String json = extractJsonObject(rawOutput);
        if (json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** Removes optional markdown fences and keeps only the outer JSON object. */
    private String extractJsonObject(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return "";
        }
        String cleaned = rawOutput.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return cleaned.substring(start, end + 1);
    }
}
