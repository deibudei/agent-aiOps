package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsDuplicateOldTextWithoutWriting() throws Exception {
        PatchTools tools = new PatchTools(toolPolicy());
        Path file = tempDir.resolve("target-service/src/main/java/App.java");
        Files.writeString(file, "class App { void a() {} void a() {} }");

        var result = tools.applyProposal(proposal(new PatchOperation(
                "target-service/src/main/java/App.java",
                "void a() {}",
                "void fixed() {}",
                "fix")));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("matched more than once");
        assertThat(Files.readString(file)).isEqualTo("class App { void a() {} void a() {} }");
    }

    @Test
    void preflightsAllOperationsBeforeWritingAnyFile() throws Exception {
        PatchTools tools = new PatchTools(toolPolicy());
        Path first = tempDir.resolve("target-service/src/main/java/App.java");
        Path second = tempDir.resolve("target-service/src/main/java/Other.java");
        Files.writeString(first, "class App { void bug() {} }");
        Files.writeString(second, "class Other { void ok() {} }");

        var result = tools.applyProposal(proposal(
                new PatchOperation(
                        "target-service/src/main/java/App.java",
                        "void bug() {}",
                        "void fixed() {}",
                        "fix"),
                new PatchOperation(
                        "target-service/src/main/java/Other.java",
                        "missing()",
                        "fixed()",
                        "fix")));

        assertThat(result.success()).isFalse();
        assertThat(Files.readString(first)).isEqualTo("class App { void bug() {} }");
        assertThat(Files.readString(second)).isEqualTo("class Other { void ok() {} }");
    }

    private ToolPolicy toolPolicy() throws Exception {
        Files.createDirectories(tempDir.resolve("target-service/src/main/java"));
        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        return new ToolPolicy(properties);
    }

    private PatchProposal proposal(PatchOperation... operations) {
        return new PatchProposal(
                "target",
                "root cause",
                List.of(operations),
                List.of("mvn -pl target-service test"),
                true,
                "");
    }
}
