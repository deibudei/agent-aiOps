package org.example.agentaiops.repair.agentic.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.example.agentaiops.repair.agentic.AgenticRepairState;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.junit.jupiter.api.Test;

class PatchParserOperatorTest {

    @Test
    void validatesTypedPatchProposal() {
        AgenticRepairState state = new AgenticRepairState("session-001", Instant.now());
        PatchParserOperator operator = new PatchParserOperator(
                state,
                new RepairEventHub());
        PatchProposal typedProposal = validProposal();

        PatchProposal proposal = operator.parsePatchProposal(typedProposal);

        assertThat(proposal.modelGenerated()).isTrue();
        assertThat(proposal.operations()).hasSize(1);
        assertThat(state.patchProposal).isSameAs(proposal);
    }

    @Test
    void rejectsNullPatchProposal() {
        PatchParserOperator operator = new PatchParserOperator(
                new AgenticRepairState("session-001", Instant.now()),
                new RepairEventHub());

        assertThatThrownBy(() -> operator.parsePatchProposal(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PatchProposal");
    }

    @Test
    void rejectsPatchProposalWithoutOperations() {
        PatchParserOperator operator = new PatchParserOperator(
                new AgenticRepairState("session-001", Instant.now()),
                new RepairEventHub());

        assertThatThrownBy(() -> operator.parsePatchProposal(new PatchProposal(
                "OrderService.calculateUnitPrice",
                "quantity is not validated before division",
                List.of(),
                List.of("mvn -pl target-service test"),
                true,
                "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("patch operations");
    }

    private PatchProposal validProposal() {
        return new PatchProposal(
                "OrderService.calculateUnitPrice",
                "quantity is not validated before division",
                List.of(new PatchOperation(
                        "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                        "return totalCents / quantity;",
                        "if (quantity <= 0) { throw new IllegalArgumentException(\"quantity\"); }\n"
                                + "return totalCents / quantity;",
                        "Reject invalid quantities before division")),
                List.of("mvn -pl target-service test"),
                true,
                "");
    }
}
