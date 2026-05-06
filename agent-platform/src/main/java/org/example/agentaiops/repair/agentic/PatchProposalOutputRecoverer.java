package org.example.agentaiops.repair.agentic;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.agentaiops.repair.model.PatchProposal;

/** Recovers PatchProposal JSON from provider outputs that contain raw multiline strings. */
final class PatchProposalOutputRecoverer {

    private static final Pattern BASE64_OUTPUT = Pattern.compile("base64: \"([A-Za-z0-9+/=]+)\"");
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private PatchProposalOutputRecoverer() {
    }

    static Optional<PatchProposal> recover(Throwable throwable) {
        for (String candidate : candidates(throwable)) {
            try {
                return Optional.of(LENIENT_MAPPER.readValue(stripCodeFence(candidate), PatchProposal.class));
            } catch (Exception ignored) {
                // Try the next candidate from the exception chain.
            }
        }
        return Optional.empty();
    }

    private static List<String> candidates(Throwable throwable) {
        List<String> values = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                Matcher matcher = BASE64_OUTPUT.matcher(message);
                while (matcher.find()) {
                    decode(matcher.group(1)).ifPresent(values::add);
                }
            }
            current = current.getCause();
        }
        return values;
    }

    private static Optional<String> decode(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFenceStart = trimmed.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFenceStart > firstLineEnd) {
            return trimmed.substring(firstLineEnd + 1, lastFenceStart).trim();
        }
        return trimmed;
    }
}
