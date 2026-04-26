package org.example.agentaiops.repair.tool;

import java.time.Duration;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.extension.TestRunner;
import org.example.agentaiops.repair.model.CommandResult;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class MavenTestRunner implements TestRunner {

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;
    private final CommandRunner commandRunner;

    /** Wires Maven command execution with workspace-root detection. */
    public MavenTestRunner(
            RepairProperties properties, ToolPolicy toolPolicy, CommandRunner commandRunner) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
        this.commandRunner = commandRunner;
    }

    /** Runs the configured target-service Maven tests from the repository root. */
    @Override
    public TestExecutionResult runTests() {
        List<String> command = List.of(mavenCommand(), "-pl", "target-service", "test");
        Duration timeout = Duration.ofSeconds(properties.getWorkflow().getProcessTimeoutSeconds());
        CommandResult result = commandRunner.run(toolPolicy.workspaceRoot(), command, timeout);
        return new TestExecutionResult(
                result.exitCode(), result.output(), "", result.durationMillis(), result.timedOut());
    }

    /** Chooses the Windows or Unix Maven launcher name. */
    private String mavenCommand() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "mvn.cmd" : "mvn";
    }
}
