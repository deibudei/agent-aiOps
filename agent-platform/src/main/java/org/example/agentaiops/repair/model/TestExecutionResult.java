package org.example.agentaiops.repair.model;

public record TestExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        long durationMillis,
        boolean timedOut) {

    public boolean success() {
        return exitCode == 0 && !timedOut;
    }
}
