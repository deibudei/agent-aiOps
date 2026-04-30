package org.example.agentaiops.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/scenarios")
public class DemoScenarioController {

    private final DemoScenarioService demoScenarioService;

    /** Wires the one-click demo scenario API. */
    public DemoScenarioController(DemoScenarioService demoScenarioService) {
        this.demoScenarioService = demoScenarioService;
    }

    /** Injects a demo fault and pauses until the operator restarts target-service. */
    @PostMapping("/start")
    public DemoScenarioResult start(@RequestBody DemoScenarioStartRequest request) {
        return demoScenarioService.start(request);
    }

    /** Returns the current scenario state. */
    @GetMapping("/{sessionId}")
    public DemoScenarioResult get(@PathVariable String sessionId) {
        return demoScenarioService.get(sessionId);
    }

    /** Confirms the manual restart, prepares evidence, and starts the repair run. */
    @PostMapping("/{sessionId}/confirm-target-restarted")
    public DemoScenarioResult confirmTargetRestarted(@PathVariable String sessionId) {
        return demoScenarioService.confirmTargetRestarted(sessionId);
    }
}
