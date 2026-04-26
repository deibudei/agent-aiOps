package org.example.agentaiops.demo;

import java.util.List;

/** Response returned after listing, injecting, or resetting demo faults. */
public record DemoFaultResult(
        String faultType,
        boolean success,
        String message,
        List<String> changedFiles,
        List<String> nextSteps) {
}
