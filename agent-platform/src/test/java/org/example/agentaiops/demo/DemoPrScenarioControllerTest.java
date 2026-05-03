package org.example.agentaiops.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DemoPrScenarioControllerTest {

    private final DemoScenarioService demoScenarioService = mock(DemoScenarioService.class);
    private final DemoTargetServiceRestarter targetServiceRestarter = mock(DemoTargetServiceRestarter.class);
    private final DemoPrScenarioController controller =
            new DemoPrScenarioController(demoScenarioService, targetServiceRestarter);

    @Test
    void restartsTargetServiceForWaitingPullRequestScenario() {
        DemoScenarioResult scenario = scenario(DemoScenarioStage.WAITING_FOR_TARGET_RESTART, "D:\\worktrees\\demo");
        DemoTargetRestartResult restartResult = new DemoTargetRestartResult(
                "demo-001",
                true,
                "target-service restarted",
                scenario.worktreePath(),
                "mvn.cmd -pl target-service spring-boot:run",
                123L,
                "target-service/logs/auto-restart-demo.log",
                List.of());
        when(demoScenarioService.get("demo-001")).thenReturn(scenario);
        when(targetServiceRestarter.restart(scenario)).thenReturn(restartResult);

        DemoTargetRestartResult result = controller.restartTargetService("demo-001");

        assertThat(result.success()).isTrue();
        assertThat(result.pid()).isEqualTo(123L);
    }

    @Test
    void rejectsRestartWhenScenarioIsNotWaiting() {
        when(demoScenarioService.get("demo-001")).thenReturn(scenario(DemoScenarioStage.RUNNING, "D:\\worktrees\\demo"));

        assertThatThrownBy(() -> controller.restartTargetService("demo-001"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not waiting");
    }

    @Test
    void rejectsRestartWhenWorktreePathIsMissing() {
        when(demoScenarioService.get("demo-001")).thenReturn(
                scenario(DemoScenarioStage.WAITING_FOR_TARGET_RESTART, ""));

        assertThatThrownBy(() -> controller.restartTargetService("demo-001"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no worktree path");
    }

    private DemoScenarioResult scenario(DemoScenarioStage stage, String worktreePath) {
        return new DemoScenarioResult(
                "demo-001",
                "quantity-division-by-zero",
                stage,
                true,
                "message",
                List.of(),
                List.of(),
                null,
                "http://localhost:9910",
                "",
                "repair/demo-001",
                worktreePath,
                "",
                null,
                "",
                "",
                "");
    }
}
