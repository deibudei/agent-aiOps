package org.example.agentaiops.demo;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/faults")
public class DemoFaultController {

    private final DemoFaultService demoFaultService;

    /** Wires the local demo fault injection API. */
    public DemoFaultController(DemoFaultService demoFaultService) {
        this.demoFaultService = demoFaultService;
    }

    /** Lists supported fault types for the competition demo. */
    @GetMapping
    public List<DemoFaultResult> listFaults() {
        return demoFaultService.listFaults();
    }

    /** Injects a selected source-level fault into target-service. */
    @PostMapping("/{faultType}/inject")
    public DemoFaultResult inject(@PathVariable String faultType) {
        return demoFaultService.inject(faultType);
    }

    /** Restores demo-touched files to their fixed baseline. */
    @PostMapping("/reset")
    public DemoFaultResult reset() {
        return demoFaultService.reset();
    }
}
