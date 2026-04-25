package org.example.agentaiops.repair.model;

public record GitCommitResult(boolean success, String branchName, String commitMessage, String message) {
}
