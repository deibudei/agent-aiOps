package org.example.agentaiops.repair.model;

/** Holds Maven test execution output and timing. */
public record TestExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        long durationMillis,
        boolean timedOut) {

    /** Treats exit code 0 without timeout as a successful test run. */
    public boolean success() {
        return exitCode == 0 && !timedOut;
    }
}
