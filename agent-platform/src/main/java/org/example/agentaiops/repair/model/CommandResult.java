package org.example.agentaiops.repair.model;

/** Holds raw process execution output from CommandRunner. */
public record CommandResult(
        int exitCode,
        String output,
        long durationMillis,
        boolean timedOut) {

    /** Treats exit code 0 without timeout as a successful command. */
    public boolean success() {
        return exitCode == 0 && !timedOut;
    }
}
