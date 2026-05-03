package org.example.agentaiops.demo;

import java.util.List;

/** Result of starting target-service from a PR-safe repair worktree. */
public record DemoTargetRestartResult(
        String sessionId,
        boolean success,
        String message,
        String worktreePath,
        String command,
        Long pid,
        String logPath,
        List<String> nextSteps) {
}
