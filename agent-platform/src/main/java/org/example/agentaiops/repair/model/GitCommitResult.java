package org.example.agentaiops.repair.model;

/** Reports optional local branch, commit, and push automation. */
public record GitCommitResult(boolean success, String branchName, String commitMessage, String message) {
}
