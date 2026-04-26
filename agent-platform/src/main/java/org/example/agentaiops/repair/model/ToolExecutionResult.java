package org.example.agentaiops.repair.model;

/** Holds output from a controlled repair tool invocation. */
public record ToolExecutionResult(
        boolean success,
        String output,
        String error) {

    /** Creates a successful tool result with stdout-like output. */
    public static ToolExecutionResult success(String output) {
        return new ToolExecutionResult(true, output, "");
    }

    /** Creates a failed tool result with an error message. */
    public static ToolExecutionResult failure(String error) {
        return new ToolExecutionResult(false, "", error);
    }
}
