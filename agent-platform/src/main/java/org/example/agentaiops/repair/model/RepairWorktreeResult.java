package org.example.agentaiops.repair.model;

/** Result of preparing an isolated repair worktree for one demo job. */
public record RepairWorktreeResult(
        boolean success,
        String branchName,
        String worktreePath,
        String message) {
}
