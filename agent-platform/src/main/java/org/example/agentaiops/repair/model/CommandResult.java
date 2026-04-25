package org.example.agentaiops.repair.model;

public record CommandResult(
        int exitCode,
        String output,
        long durationMillis,
        boolean timedOut) {

    public boolean success() {
        return exitCode == 0 && !timedOut;
    }
}
