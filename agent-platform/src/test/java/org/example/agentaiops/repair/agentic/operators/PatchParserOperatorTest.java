package org.example.agentaiops.repair.agentic.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.example.agentaiops.llm.StructuredJsonParser;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.junit.jupiter.api.Test;

class PatchParserOperatorTest {

    @Test
    void parsesValidPatchProposalJson() {
        AgenticRepairState state = new AgenticRepairState("session-001", Instant.now());
        PatchParserOperator operator = new PatchParserOperator(
                state,
                new StructuredJsonParser(new ObjectMapper()),
                new RepairEventHub());
        String patchJson = """
                {
                  "repairTarget": "OrderService.calculateUnitPrice",
                  "rootCause": "quantity is not validated before division",
                  "operations": [
                    {
                      "filePath": "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                      "oldText": "return totalCents / quantity;",
                      "newText": "if (quantity <= 0) { throw new IllegalArgumentException(\\"quantity\\"); }\\nreturn totalCents / quantity;",
                      "reason": "Reject invalid quantities before division"
                    }
                  ],
                  "testsToRun": ["mvn -pl target-service test"]
                }
                """;

        PatchProposal proposal = operator.parsePatchProposal(patchJson);

        assertThat(proposal.modelGenerated()).isTrue();
        assertThat(proposal.rawModelOutput()).isEqualTo(patchJson);
        assertThat(proposal.operations()).hasSize(1);
        assertThat(state.patchProposal).isSameAs(proposal);
    }

    @Test
    void rejectsInvalidPatchProposalJson() {
        PatchParserOperator operator = new PatchParserOperator(
                new AgenticRepairState("session-001", Instant.now()),
                new StructuredJsonParser(new ObjectMapper()),
                new RepairEventHub());

        assertThatThrownBy(() -> operator.parsePatchProposal("not json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PatchProposal");
    }

    @Test
    void rejectsPatchProposalWithoutOperations() {
        PatchParserOperator operator = new PatchParserOperator(
                new AgenticRepairState("session-001", Instant.now()),
                new StructuredJsonParser(new ObjectMapper()),
                new RepairEventHub());

        assertThatThrownBy(() -> operator.parsePatchProposal("""
                {
                  "repairTarget": "OrderService.calculateUnitPrice",
                  "rootCause": "quantity is not validated before division",
                  "operations": [],
                  "testsToRun": ["mvn -pl target-service test"]
                }
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("patch operations");
    }
}
