package org.example.agentaiops.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/pr-scenarios")
public class DemoPrScenarioController {

    private final DemoScenarioService demoScenarioService;

    /** Wires the PR-safe one-click demo scenario API. */
    public DemoPrScenarioController(DemoScenarioService demoScenarioService) {
        this.demoScenarioService = demoScenarioService;
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

    /** Confirms target-service restart, prepares evidence, and starts the PR repair workflow. */
    @PostMapping("/{sessionId}/confirm-target-restarted")
    public DemoScenarioResult confirmTargetRestarted(@PathVariable String sessionId) {
        return demoScenarioService.confirmPullRequestTargetRestarted(sessionId);
    }
}
