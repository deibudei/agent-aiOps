package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadLogToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsPlainStartupLogsAsTraceback() throws Exception {
        ReadLogTools tools = toolsFor("""
                2026-04-26 INFO Starting TargetServiceApplication
                2026-04-26 INFO Tomcat started on port 9910
                """);

        ToolExecutionResult result = tools.readLatestTraceback(2000);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("No exception marker");
    }

    @Test
    void extractsLatestExceptionWindow() throws Exception {
        ReadLogTools tools = toolsFor("""
                2026-04-26 INFO Starting TargetServiceApplication
                2026-04-26 ERROR Servlet.service() failed
                java.lang.ArithmeticException: / by zero
                \tat com.example.targetservice.service.OrderService.calculateUnitPrice(OrderService.java:8)
                """);

        ToolExecutionResult result = tools.readLatestTraceback(2000);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("ArithmeticException");
        assertThat(result.output()).contains("OrderService.calculateUnitPrice");
    }

    @Test
    void readsLatestTracebackFromLogDirectory() throws Exception {
        Path logsDir = tempDir.resolve("target-service/logs");
        Path tracebacksDir = logsDir.resolve("tracebacks");
        Files.createDirectories(tracebacksDir);
        Files.writeString(logsDir.resolve("target-service.log"), "2026-04-26 INFO Started");

        Path oldTraceback = tracebacksDir.resolve("traceback-20260426-100000-000-old.log");
        Files.writeString(oldTraceback, """
                java.lang.IllegalStateException: old failure
                \tat com.example.Old.fail(Old.java:1)
                """);
        Files.setLastModifiedTime(oldTraceback, FileTime.fromMillis(1000));

        Path newTraceback = tracebacksDir.resolve("traceback-20260426-100001-000-new.log");
        Files.writeString(newTraceback, """
                java.lang.ArithmeticException: / by zero
                \tat com.example.targetservice.service.OrderService.calculateUnitPrice(OrderService.java:8)
                """);
        Files.setLastModifiedTime(newTraceback, FileTime.fromMillis(2000));

        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.getTargetProject().setLogPath("target-service/logs");
        ReadLogTools tools = new ReadLogTools(properties, new ToolPolicy(properties));

        ToolExecutionResult result = tools.readLatestTraceback(2000);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("traceback-20260426-100001-000-new.log");
        assertThat(result.output()).contains("ArithmeticException");
        assertThat(result.output()).doesNotContain("old failure");
    }

    /** Builds the log reader against a temporary target-service log file. */
    private ReadLogTools toolsFor(String logContent) throws Exception {
        Path logPath = tempDir.resolve("target-service/logs/target-service.log");
        Files.createDirectories(logPath.getParent());
        Files.writeString(logPath, logContent);

        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.getTargetProject().setLogPath("target-service/logs/target-service.log");
        return new ReadLogTools(properties, new ToolPolicy(properties));
    }
}
