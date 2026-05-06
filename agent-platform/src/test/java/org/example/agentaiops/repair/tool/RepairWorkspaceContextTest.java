package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RepairWorkspaceContextTest {

    @Test
    void carriesBaseBranchOnlyWithinWorkspaceScope() {
        RepairWorkspaceContext context = new RepairWorkspaceContext();
        Path workspace = Path.of("D:/tmp/worktree");

        String branch = context.callWithWorkspace(workspace, "demo/fault/precision-loss",
                () -> context.activeBaseBranch().orElse(""));

        assertThat(branch).isEqualTo("demo/fault/precision-loss");
        assertThat(context.activeBaseBranch()).isEmpty();
    }
}
