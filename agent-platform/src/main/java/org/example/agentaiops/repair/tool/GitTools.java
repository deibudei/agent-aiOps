package org.example.agentaiops.repair.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.CommandResult;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.springframework.stereotype.Component;

@Component
public class GitTools {

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;
    private final CommandRunner commandRunner;

    public GitTools(RepairProperties properties, ToolPolicy toolPolicy, CommandRunner commandRunner) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
        this.commandRunner = commandRunner;
    }

    public String readTargetDiff() {
        CommandResult result = runGit("diff", "--", "target-service");
        return stripGitWarnings(result.output());
    }

    public List<String> changedTargetFiles() {
        CommandResult result = runGit("diff", "--name-only", "--", "target-service");
        if (!result.success() || result.output().isBlank()) {
            return List.of();
        }
        return parseChangedFiles(result.output());
    }

    static List<String> parseChangedFiles(String output) {
        return Arrays.stream(output.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !isGitWarning(value))
                .toList();
    }

    private static String stripGitWarnings(String output) {
        return Arrays.stream(output.split("\\R"))
                .filter(value -> !isGitWarning(value.trim()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    private static boolean isGitWarning(String value) {
        return value.startsWith("warning: ");
    }

    public GitCommitResult commitAndPush(String sessionId) {
        if (!properties.getGit().isEnabled()) {
            return new GitCommitResult(true, "", "", "Git is disabled; skipped commit and push");
        }

        String branchName = "repair/" + sanitize(sessionId);
        String commitMessage = "fix: auto repair target-service validation";
        List<String> failures = new ArrayList<>();

        CommandResult checkout = runGit("checkout", "-b", branchName);
        if (!checkout.success()) {
            failures.add(checkout.output());
        }

        CommandResult add = runGit("add", "target-service");
        if (!add.success()) {
            failures.add(add.output());
        }

        CommandResult commit = runGit("commit", "-m", commitMessage);
        if (!commit.success()) {
            failures.add(commit.output());
        }

        CommandResult push = runGit("push", "-u", properties.getGit().getRemote(), branchName);
        if (!push.success()) {
            failures.add(push.output());
        }

        if (!failures.isEmpty()) {
            return new GitCommitResult(false, branchName, commitMessage, String.join(System.lineSeparator(), failures));
        }

        return new GitCommitResult(true, branchName, commitMessage, "Branch pushed");
    }

    public boolean allChangedFilesAllowed(List<String> changedFiles) {
        return changedFiles.stream().allMatch(toolPolicy::isAllowedChangedFile);
    }

    private CommandResult runGit(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return commandRunner.run(
                toolPolicy.workspaceRoot(),
                command,
                Duration.ofSeconds(properties.getWorkflow().getProcessTimeoutSeconds()));
    }

    private String sanitize(String sessionId) {
        return sessionId.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
