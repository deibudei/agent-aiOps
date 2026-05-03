package org.example.agentaiops.repair.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.CommandResult;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.RepairDiffFile;
import org.example.agentaiops.repair.model.RepairDiffHunk;
import org.example.agentaiops.repair.model.RepairDiffLine;
import org.example.agentaiops.repair.model.RepairWorktreeResult;
import org.springframework.stereotype.Component;

@Component
public class GitTools {

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

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

    /** Reads and parses the target-service diff for PR-style frontend review. */
    public List<RepairDiffFile> readTargetDiffFiles() {
        return parseDiffFiles(readTargetDiff());
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

    /** Parses git unified diff output into file/hunk/line records for UI rendering. */
    public static List<RepairDiffFile> parseDiffFiles(String diff) {
        if (diff == null || diff.isBlank()) {
            return List.of();
        }

        List<RepairDiffFile> files = new ArrayList<>();
        DiffFileBuilder currentFile = null;
        DiffHunkBuilder currentHunk = null;

        for (String line : diff.split("\\R", -1)) {
            Matcher diffMatcher = DIFF_HEADER.matcher(line);
            if (diffMatcher.matches()) {
                if (currentFile != null) {
                    currentFile.addHunk(currentHunk);
                    files.add(currentFile.build());
                }
                currentFile = new DiffFileBuilder(diffMatcher.group(1), diffMatcher.group(2));
                currentHunk = null;
                continue;
            }

            if (currentFile == null) {
                continue;
            }

            if (line.startsWith("new file mode ")) {
                currentFile.status = "added";
                continue;
            }
            if (line.startsWith("deleted file mode ")) {
                currentFile.status = "deleted";
                continue;
            }
            if (line.startsWith("rename from ")) {
                currentFile.status = "renamed";
                currentFile.oldPath = line.substring("rename from ".length());
                continue;
            }
            if (line.startsWith("rename to ")) {
                currentFile.status = "renamed";
                currentFile.newPath = line.substring("rename to ".length());
                currentFile.filePath = currentFile.newPath;
                continue;
            }
            if (line.startsWith("--- ")) {
                currentFile.oldPath = normalizeDiffPath(line.substring(4));
                if ("/dev/null".equals(currentFile.oldPath)) {
                    currentFile.status = "added";
                }
                continue;
            }
            if (line.startsWith("+++ ")) {
                currentFile.newPath = normalizeDiffPath(line.substring(4));
                if ("/dev/null".equals(currentFile.newPath)) {
                    currentFile.status = "deleted";
                    currentFile.filePath = currentFile.oldPath;
                } else {
                    currentFile.filePath = currentFile.newPath;
                }
                continue;
            }

            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.matches()) {
                currentFile.addHunk(currentHunk);
                currentHunk = new DiffHunkBuilder(
                        line,
                        parseInt(hunkMatcher.group(1)),
                        parseOptionalCount(hunkMatcher.group(2)),
                        parseInt(hunkMatcher.group(3)),
                        parseOptionalCount(hunkMatcher.group(4)));
                continue;
            }

            if (currentHunk == null) {
                continue;
            }

            if (line.startsWith("+")) {
                currentHunk.addAdded(line.substring(1));
                currentFile.additions++;
            } else if (line.startsWith("-")) {
                currentHunk.addDeleted(line.substring(1));
                currentFile.deletions++;
            } else if (line.startsWith(" ")) {
                currentHunk.addContext(line.substring(1));
            } else if (line.startsWith("\\")) {
                currentHunk.addMeta(line);
            }
        }

        if (currentFile != null) {
            currentFile.addHunk(currentHunk);
            files.add(currentFile.build());
        }
        return files;
    }

    private static String normalizeDiffPath(String path) {
        if (path == null || path.isBlank() || "/dev/null".equals(path)) {
            return "/dev/null";
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("a/") || trimmed.startsWith("b/")) {
            return trimmed.substring(2);
        }
        return trimmed;
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value);
    }

    private static int parseOptionalCount(String value) {
        return value == null || value.isBlank() ? 1 : Integer.parseInt(value);
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

    private static final class DiffFileBuilder {
        private String filePath;
        private String oldPath;
        private String newPath;
        private String status = "modified";
        private int additions;
        private int deletions;
        private final List<RepairDiffHunk> hunks = new ArrayList<>();

        DiffFileBuilder(String oldPath, String newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
            this.filePath = newPath;
        }

        void addHunk(DiffHunkBuilder hunk) {
            if (hunk != null) {
                hunks.add(hunk.build());
            }
        }

        RepairDiffFile build() {
            String resolvedPath = hasDiffPath(filePath) ? filePath : hasDiffPath(newPath) ? newPath : oldPath;
            return new RepairDiffFile(
                    resolvedPath,
                    oldPath,
                    newPath,
                    status,
                    additions,
                    deletions,
                    List.copyOf(hunks));
        }

        private boolean hasDiffPath(String path) {
            return path != null && !path.isBlank() && !"/dev/null".equals(path);
        }
    }

    private static final class DiffHunkBuilder {
        private final String header;
        private final int oldStart;
        private final int oldLines;
        private final int newStart;
        private final int newLines;
        private final List<RepairDiffLine> lines = new ArrayList<>();
        private int oldLineNumber;
        private int newLineNumber;

        DiffHunkBuilder(String header, int oldStart, int oldLines, int newStart, int newLines) {
            this.header = header;
            this.oldStart = oldStart;
            this.oldLines = oldLines;
            this.newStart = newStart;
            this.newLines = newLines;
            this.oldLineNumber = oldStart;
            this.newLineNumber = newStart;
        }

        void addAdded(String content) {
            lines.add(new RepairDiffLine("add", null, newLineNumber, content));
            newLineNumber++;
        }

        void addDeleted(String content) {
            lines.add(new RepairDiffLine("delete", oldLineNumber, null, content));
            oldLineNumber++;
        }

        void addContext(String content) {
            lines.add(new RepairDiffLine("context", oldLineNumber, newLineNumber, content));
            oldLineNumber++;
            newLineNumber++;
        }

        void addMeta(String content) {
            lines.add(new RepairDiffLine("meta", null, null, content));
        }

        RepairDiffHunk build() {
            return new RepairDiffHunk(
                    header,
                    oldStart,
                    oldLines,
                    newStart,
                    newLines,
                    List.copyOf(lines));
        }
    }
}
