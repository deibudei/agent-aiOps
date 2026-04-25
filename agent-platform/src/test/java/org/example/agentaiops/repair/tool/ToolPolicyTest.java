package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.example.agentaiops.config.RepairProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void allowsOnlyTargetServiceWriteRoots() throws Exception {
        Files.createDirectories(tempDir.resolve("target-service/src/main/java"));
        Files.createDirectories(tempDir.resolve("target-service/src/test/java"));
        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());

        ToolPolicy policy = new ToolPolicy(properties);

        assertThat(policy.resolveForWrite("target-service/src/main/java/App.java").toString().replace('\\', '/'))
                .endsWith("target-service/src/main/java/App.java");
        assertThat(policy.resolveForWrite("target-service/src/test/java/AppTest.java").toString().replace('\\', '/'))
                .endsWith("target-service/src/test/java/AppTest.java");
        assertThatThrownBy(() -> policy.resolveForWrite("agent-platform/src/main/java/App.java"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discoversRepositoryRootWhenStartedFromAgentModule() throws Exception {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createDirectories(tempDir.resolve("agent-platform"));
        Files.createFile(tempDir.resolve("agent-platform/pom.xml"));
        Files.createDirectories(tempDir.resolve("target-service/src/main/java"));
        Files.createFile(tempDir.resolve("target-service/pom.xml"));

        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.resolve("agent-platform").toString());

        ToolPolicy policy = new ToolPolicy(properties);

        assertThat(policy.workspaceRoot()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(policy.resolveForRead("target-service/src/main/java/App.java"))
                .isEqualTo(tempDir.resolve("target-service/src/main/java/App.java").toAbsolutePath().normalize());
    }
}
