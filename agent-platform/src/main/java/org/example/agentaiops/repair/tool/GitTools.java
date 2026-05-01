package org.example.agentaiops.repair.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.CommandResult;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.RepairWorktreeResult;
import org.springframework.stereotype.Component;

@Component
public class GitTools {

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;
    private final CommandRunner commandRunner;

    /** Wires git config, path policy, and command execution. */
    public GitTools(RepairProperties properties, ToolPolicy toolPolicy, CommandRunner commandRunner) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
        this.commandRunner = commandRunner;
    }

    /** Reads the target-service diff for review and repair records. */
    public String readTargetDiff() {
        CommandResult result = runGit("diff", "--", "target-service");
        return stripGitWarnings(result.output());
    }

    /** Lists changed target-service files from the working tree diff. */
    public List<String> changedTargetFiles() {
        CommandResult result = runGit("diff", "--name-only", "--", "target-service");
        if (!result.success() || result.output().isBlank()) {
            return List.of();
        }
        return parseChangedFiles(result.output());
    }

    /** Parses git output while ignoring environment warning lines. */
    static List<String> parseChangedFiles(String output) {
        return Arrays.stream(output.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !isGitWarning(value))
                .toList();
    }

    /** Removes warning lines that should not appear in demo records. */
    private static String stripGitWarnings(String output) {
        return Arrays.stream(output.split("\\R"))
                .filter(value -> !isGitWarning(value.trim()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    /** Detects git warnings emitted by the local Windows environment. */
    private static boolean isGitWarning(String value) {
        return value.startsWith("warning: ");
    }

    /** Creates a repair branch, commits target-service changes, and pushes it when enabled. */
    public GitCommitResult commitAndPush(String sessionId, String repairTarget) {
        if (!properties.getGit().isEnabled()) {
            return new GitCommitResult(true, "", "", "Git is disabled; skipped commit and push");
        }

        String branchName = "repair/" + sanitize(sessionId);
        String safeTarget = repairTarget == null || repairTarget.isBlank() ? "target-service repair" : repairTarget;
        String commitMessage = "fix(repair): " + safeTarget;
        String baseBranch = properties.getGit().getBaseBranch();

        if (!branchName.equals(currentBranch())) {
            if (hasText(baseBranch)) {
                CommandResult fetch = runGit("fetch", properties.getGit().getRemote(), baseBranch);
                if (!fetch.success()) {
                    return failed(branchName, commitMessage, fetch);
                }
                CommandResult checkoutBase = runGit("checkout", baseBranch);
                if (!checkoutBase.success()) {
                    CommandResult checkoutFromRemote = runGit(
                            "checkout", "-B", baseBranch, properties.getGit().getRemote() + "/" + baseBranch);
                    if (!checkoutFromRemote.success()) {
                        return new GitCommitResult(false, branchName, commitMessage,
                                checkoutBase.output() + System.lineSeparator() + checkoutFromRemote.output());
                    }
                }
            }

            CommandResult checkout = runGit("checkout", "-b", branchName);
            if (!checkout.success()) {
                return failed(branchName, commitMessage, checkout);
            }
        }

        CommandResult add = runGit("add", "target-service");
        if (!add.success()) {
            return failed(branchName, commitMessage, add);
        }

        CommandResult commit = runGit("commit", "-m", commitMessage);
        if (!commit.success()) {
            return failed(branchName, commitMessage, commit);
        }

        CommandResult push = runGit("push", "-u", properties.getGit().getRemote(), branchName);
        if (!push.success()) {
            return failed(branchName, commitMessage, push);
        }

        return new GitCommitResult(true, branchName, commitMessage, "Branch pushed");
    }

    /** Prepares a clean repair branch from the configured committed demo fault base branch. */
    public GitCommitResult prepareRepairBranchFromBase(String sessionId) {
        if (!properties.getGit().isEnabled()) {
            return new GitCommitResult(false, "", "", "Git is disabled; cannot prepare PR demo branch");
        }
        String baseBranch = properties.getGit().getBaseBranch();
        if (!hasText(baseBranch)) {
            return new GitCommitResult(false, "", "", "repair.git.base-branch is empty");
        }
        if (!workingTreeClean()) {
            return new GitCommitResult(
                    false,
                    "",
                    "",
                    "Working tree must be clean before a PR demo scenario can switch branches. "
                            + "Commit, stash, or reset local changes first.");
        }

        String branchName = "repair/" + sanitize(sessionId);
        String commitMessage = "fix(repair): PR demo scenario";
        CommandResult fetch = runGit("fetch", properties.getGit().getRemote(), baseBranch);
        if (!fetch.success()) {
            return failed(branchName, commitMessage, fetch);
        }

        CommandResult checkoutBase = runGit("checkout", baseBranch);
        if (!checkoutBase.success()) {
            CommandResult checkoutFromRemote = runGit(
                    "checkout", "-B", baseBranch, properties.getGit().getRemote() + "/" + baseBranch);
            if (!checkoutFromRemote.success()) {
                return new GitCommitResult(false, branchName, commitMessage,
                        checkoutBase.output() + System.lineSeparator() + checkoutFromRemote.output());
            }
        }

        CommandResult checkoutRepair = runGit("checkout", "-B", branchName);
        if (!checkoutRepair.success()) {
            return failed(branchName, commitMessage, checkoutRepair);
        }
        return new GitCommitResult(true, branchName, commitMessage,
                "Prepared " + branchName + " from " + baseBranch);
    }

    /** Prepares an isolated repair worktree from the configured committed demo fault base branch. */
    public RepairWorktreeResult prepareRepairWorktreeFromBase(String sessionId) {
        if (!properties.getGit().isEnabled()) {
            return new RepairWorktreeResult(false, "", "", "Git is disabled; cannot prepare PR demo worktree");
        }
        String baseBranch = properties.getGit().getBaseBranch();
        if (!hasText(baseBranch)) {
            return new RepairWorktreeResult(false, "", "", "repair.git.base-branch is empty");
        }

        String branchName = "repair/" + sanitize(sessionId);
        Path worktreePath;
        try {
            worktreePath = worktreePath(sessionId);
            if (Files.exists(worktreePath)) {
                return new RepairWorktreeResult(
                        false,
                        branchName,
                        worktreePath.toString(),
                        "Repair worktree already exists: " + worktreePath);
            }
            Files.createDirectories(worktreePath.getParent());
        } catch (IOException | IllegalArgumentException e) {
            return new RepairWorktreeResult(false, branchName, "", e.getMessage());
        }

        CommandResult fetch = runGitAt(toolPolicy.homeWorkspaceRoot(), "fetch", properties.getGit().getRemote(), baseBranch);
        if (!fetch.success()) {
            return failedWorktree(branchName, worktreePath, fetch);
        }

        String remoteBase = properties.getGit().getRemote() + "/" + baseBranch;
        CommandResult add = runGitAt(
                toolPolicy.homeWorkspaceRoot(),
                "worktree", "add", "-B", branchName, worktreePath.toString(), remoteBase);
        if (!add.success()) {
            CommandResult fallback = runGitAt(
                    toolPolicy.homeWorkspaceRoot(),
                    "worktree", "add", "-B", branchName, worktreePath.toString(), baseBranch);
            if (!fallback.success()) {
                return new RepairWorktreeResult(
                        false,
                        branchName,
                        worktreePath.toString(),
                        add.output() + System.lineSeparator() + fallback.output());
            }
        }
        return new RepairWorktreeResult(
                true,
                branchName,
                worktreePath.toString(),
                "Prepared " + branchName + " in " + worktreePath + " from " + baseBranch);
    }

    private GitCommitResult failed(String branchName, String commitMessage, CommandResult commandResult) {
        return new GitCommitResult(false, branchName, commitMessage, commandResult.output());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Confirms every changed file stays inside the write whitelist. */
    public boolean allChangedFilesAllowed(List<String> changedFiles) {
        return changedFiles.stream().allMatch(toolPolicy::isAllowedChangedFile);
    }

    /** Runs a git command from the repository root. */
    private CommandResult runGit(String... args) {
        return runGitAt(toolPolicy.workspaceRoot(), args);
    }

    /** Runs a git command from a specific repository root. */
    private CommandResult runGitAt(Path workspaceRoot, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return commandRunner.run(
                workspaceRoot,
                command,
                Duration.ofSeconds(properties.getWorkflow().getProcessTimeoutSeconds()));
    }

    /** Converts a session id into a safe branch suffix. */
    private String sanitize(String sessionId) {
        return sessionId.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private String currentBranch() {
        CommandResult result = runGit("branch", "--show-current");
        if (!result.success()) {
            return "";
        }
        return stripGitWarnings(result.output()).trim();
    }

    private boolean workingTreeClean() {
        CommandResult result = runGit("status", "--porcelain");
        if (!result.success()) {
            return false;
        }
        return parseChangedFiles(result.output()).isEmpty();
    }

    private RepairWorktreeResult failedWorktree(
            String branchName, Path worktreePath, CommandResult commandResult) {
        return new RepairWorktreeResult(false, branchName, worktreePath.toString(), commandResult.output());
    }

    private Path worktreePath(String sessionId) {
        Path root = resolveWorktreeRoot();
        Path candidate = root.resolve(sanitize(sessionId)).toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Repair worktree path escapes worktree root: " + candidate);
        }
        return candidate;
    }

    private Path resolveWorktreeRoot() {
        String configured = properties.getGit().getWorktreeRoot();
        Path raw = Paths.get(configured == null || configured.isBlank()
                ? "../agent-aiOps-worktrees"
                : configured);
        Path root = raw.isAbsolute()
                ? raw.toAbsolutePath().normalize()
                : toolPolicy.homeWorkspaceRoot().resolve(raw).toAbsolutePath().normalize();
        if (root.equals(toolPolicy.homeWorkspaceRoot()) || root.startsWith(toolPolicy.homeWorkspaceRoot())) {
            throw new IllegalArgumentException("Repair worktree root must be outside the main workspace: " + root);
        }
        return root;
    }
}
