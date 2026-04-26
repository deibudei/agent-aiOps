package org.example.agentaiops.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoFaultServiceTest {

    @TempDir
    Path tempDir;

    private DemoFaultService demoFaultService;

    @BeforeEach
    void setUp() throws Exception {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createDirectories(tempDir.resolve("agent-platform"));
        Files.createFile(tempDir.resolve("agent-platform/pom.xml"));
        Files.createDirectories(tempDir.resolve("target-service/src/main/java/com/example/targetservice/service"));
        Files.createDirectories(tempDir.resolve("target-service/src/main/java/com/example/targetservice/controller"));
        Files.createDirectories(tempDir.resolve("target-service/src/main/java/com/example/targetservice/web"));
        Files.createFile(tempDir.resolve("target-service/pom.xml"));

        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        ToolPolicy toolPolicy = new ToolPolicy(properties);
        demoFaultService = new DemoFaultService(toolPolicy);
        demoFaultService.reset();
    }

    @Test
    void injectsQuantityDivisionByZeroFault() throws Exception {
        DemoFaultResult result = demoFaultService.inject("quantity-division-by-zero");

        String content = Files.readString(tempDir.resolve(
                "target-service/src/main/java/com/example/targetservice/service/OrderService.java"));
        assertThat(result.success()).isTrue();
        assertThat(content).contains("return totalCents / quantity;");
        assertThat(content).doesNotContain("quantity must be greater than 0");
    }

    @Test
    void resetRestoresFixedBaseline() throws Exception {
        demoFaultService.inject("wrong-error-status");
        DemoFaultResult result = demoFaultService.reset();

        String content = Files.readString(tempDir.resolve(
                "target-service/src/main/java/com/example/targetservice/web/GlobalExceptionHandler.java"));
        assertThat(result.success()).isTrue();
        assertThat(content).contains("ResponseEntity.status(HttpStatus.BAD_REQUEST)");
        assertThat(content).contains("@ExceptionHandler(Exception.class)");
        assertThat(content).contains("tracebackWriter.write(exception, request)");
    }
}
