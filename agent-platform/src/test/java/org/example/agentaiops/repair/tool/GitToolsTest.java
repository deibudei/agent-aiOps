package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.CommandResult;
import org.example.agentaiops.repair.model.RepairDiffFile;
import org.example.agentaiops.repair.model.RepairWorktreeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void parseChangedFilesIgnoresGitWarnings() {
        String output = """
                warning: in the working copy of 'target-service/src/main/java/com/example/targetservice/service/OrderService.java', LF will be replaced by CRLF the next time Git touches it
                target-service/src/main/java/com/example/targetservice/service/OrderService.java
                """;

        assertThat(GitTools.parseChangedFiles(output))
                .containsExactly("target-service/src/main/java/com/example/targetservice/service/OrderService.java");
    }

    @Test
    void parsesUnifiedDiffForFrontendReview() {
        String diff = """
                diff --git a/target-service/src/main/java/com/example/targetservice/service/OrderService.java b/target-service/src/main/java/com/example/targetservice/service/OrderService.java
                index 409cf9a..2a0dbbb 100644
                --- a/target-service/src/main/java/com/example/targetservice/service/OrderService.java
                +++ b/target-service/src/main/java/com/example/targetservice/service/OrderService.java
                @@ -10,6 +10,9 @@ public class OrderService {
                     public int calculateUnitPrice(int totalCents, int quantity) {
                -        return totalCents / quantity;
                +        if (quantity <= 0) {
                +            throw new IllegalArgumentException("quantity must be positive");
                +        }
                +        return totalCents / quantity;
                     }
                 }
                """;

        RepairDiffFile file = GitTools.parseDiffFiles(diff).get(0);

        assertThat(file.filePath())
                .isEqualTo("target-service/src/main/java/com/example/targetservice/service/OrderService.java");
        assertThat(file.status()).isEqualTo("modified");
        assertThat(file.additions()).isEqualTo(4);
        assertThat(file.deletions()).isEqualTo(1);
        assertThat(file.hunks()).hasSize(1);
        assertThat(file.hunks().get(0).lines())
                .extracting("type")
                .contains("context", "delete", "add");
    }

    @Test
    void preparesRepairWorktreeWithoutSwitchingMainWorkspace() throws Exception {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createDirectories(tempDir.resolve("agent-platform"));
        Files.createFile(tempDir.resolve("agent-platform/pom.xml"));
        Files.createDirectories(tempDir.resolve("target-service"));
        Files.createFile(tempDir.resolve("target-service/pom.xml"));
        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.getGit().setEnabled(true);
        properties.getGit().setBaseBranch("demo/fault/quantity-division-by-zero");
        properties.getGit().setWorktreeRoot("../worktrees");
        CommandRunner runner = mock(CommandRunner.class);
        when(runner.run(eq(tempDir.toAbsolutePath().normalize()), any(), any()))
                .thenReturn(new CommandResult(0, "ok", 1, false));

        GitTools tools = new GitTools(properties, new ToolPolicy(properties), runner);

        RepairWorktreeResult result = tools.prepareRepairWorktreeFromBase("scenario-001");

        assertThat(result.success()).isTrue();
        assertThat(result.branchName()).isEqualTo("repair/scenario-001");
        assertThat(result.worktreePath()).contains("worktrees");
        verify(runner).run(
                eq(tempDir.toAbsolutePath().normalize()),
                argThat(command -> command.contains("fetch")),
                any());
        verify(runner).run(
                eq(tempDir.toAbsolutePath().normalize()),
                argThat(command -> command.contains("worktree") && command.contains("add")),
                any());
    }

    @Test
    void commitStagesOnlyTargetSourceAndTestRoots() throws Exception {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createDirectories(tempDir.resolve("agent-platform"));
        Files.createFile(tempDir.resolve("agent-platform/pom.xml"));
        Files.createDirectories(tempDir.resolve("target-service/src/main"));
        Files.createDirectories(tempDir.resolve("target-service/src/test"));
        Files.createFile(tempDir.resolve("target-service/pom.xml"));
        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.getGit().setEnabled(true);
        CommandRunner runner = mock(CommandRunner.class);
        when(runner.run(eq(tempDir.toAbsolutePath().normalize()), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> command = invocation.getArgument(1);
                    if (command.equals(java.util.List.of("git", "branch", "--show-current"))) {
                        return new CommandResult(0, "repair/session-001", 1, false);
                    }
                    if (command.equals(java.util.List.of(
                            "git", "diff", "--cached", "--name-only", "--", "target-service"))) {
                        return new CommandResult(
                                0,
                                "target-service/src/main/java/com/example/targetservice/service/OrderService.java",
                                1,
                                false);
                    }
                    return new CommandResult(0, "ok", 1, false);
                });

        GitTools tools = new GitTools(properties, new ToolPolicy(properties), runner);

        assertThat(tools.commitAndPush("session-001", "precision fix").success()).isTrue();
        verify(runner).run(
                eq(tempDir.toAbsolutePath().normalize()),
                argThat(command -> command.equals(java.util.List.of(
                        "git",
                        "add",
                        "--",
                        "target-service/src/main",
                        "target-service/src/test"))),
                any());
    }
}
