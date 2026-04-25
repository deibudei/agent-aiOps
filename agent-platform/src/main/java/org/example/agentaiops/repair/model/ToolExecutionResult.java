package org.example.agentaiops.repair.model;

public record ToolExecutionResult(
        boolean success,
        String output,
        String error) {

    public static ToolExecutionResult success(String output) {
        return new ToolExecutionResult(true, output, "");
    }

    public static ToolExecutionResult failure(String error) {
        return new ToolExecutionResult(false, "", error);
    }
}
