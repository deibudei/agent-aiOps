package org.example.agentaiops.repair.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PatchProposalOutputRecovererTest {

    @Test
    void recoversPatchProposalWithRawMultilineTextFromBase64ExceptionMessage() {
        String raw = """
                {
                  "repairTarget": "fix precision",
                  "rootCause": "double math",
                  "operations": [
                    {
                      "filePath": "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                      "oldText": "line one
                line two",
                      "newText": "line one
                line three",
                      "reason": "use BigDecimal"
                    }
                  ],
                  "testsToRun": ["mvn -pl target-service test"],
                  "modelGenerated": true,
                  "rawModelOutput": ""
                }
                """;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        RuntimeException exception = new RuntimeException(
                "OutputParsingException: Failed to parse output (base64: \"" + encoded + "\") into PatchProposal");

        var recovered = PatchProposalOutputRecoverer.recover(exception);

        assertThat(recovered).isPresent();
        assertThat(recovered.orElseThrow().operations().get(0).oldText())
                .isEqualTo("line one\nline two");
        assertThat(recovered.orElseThrow().testsToRun()).containsExactly("mvn -pl target-service test");
    }
}
