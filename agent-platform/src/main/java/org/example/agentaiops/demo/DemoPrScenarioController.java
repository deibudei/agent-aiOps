package org.example.agentaiops.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/demo/pr-scenarios")
public class DemoPrScenarioController {

    private final DemoScenarioService demoScenarioService;
    private final DemoTargetServiceRestarter targetServiceRestarter;

    /** Wires the PR-safe one-click demo scenario API. */
    public DemoPrScenarioController(
            DemoScenarioService demoScenarioService,
            DemoTargetServiceRestarter targetServiceRestarter) {
        this.demoScenarioService = demoScenarioService;
        this.targetServiceRestarter = targetServiceRestarter;
    }

    /** Prepares repair/{sessionId} from the configured committed fault base branch. */
    @PostMapping("/start")
    public DemoScenarioResult start(@RequestBody DemoScenarioStartRequest request) {
        return demoScenarioService.startPullRequestScenario(request);
    }

    /** Returns the current PR scenario state. */
    @GetMapping("/{sessionId}")
    public DemoScenarioResult get(@PathVariable String sessionId) {
        return demoScenarioService.get(sessionId);
    }

    /** Returns read-only readiness information for the selected PR-safe demo fault. */
    @GetMapping("/readiness")
    public DemoPrScenarioReadiness readiness(@RequestParam String faultType) {
        return demoScenarioService.pullRequestReadiness(faultType);
    }

    /** Confirms target-service restart, prepares evidence, and starts the PR repair workflow. */
    @PostMapping("/{sessionId}/confirm-target-restarted")
    public DemoScenarioResult confirmTargetRestarted(@PathVariable String sessionId) {
        return demoScenarioService.confirmPullRequestTargetRestarted(sessionId);
    }

    /** Restarts target-service from the prepared PR-safe worktree for local demos. */
    @PostMapping("/{sessionId}/restart-target-service")
    public DemoTargetRestartResult restartTargetService(@PathVariable String sessionId) {
        DemoScenarioResult scenario = demoScenarioService.get(sessionId);
        if (scenario.stage() != DemoScenarioStage.WAITING_FOR_TARGET_RESTART) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PR demo scenario is not waiting for target-service restart: " + sessionId);
        }
        if (scenario.worktreePath() == null || scenario.worktreePath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PR demo scenario has no worktree path: " + sessionId);
        }
        return targetServiceRestarter.restart(scenario);
    }
}
