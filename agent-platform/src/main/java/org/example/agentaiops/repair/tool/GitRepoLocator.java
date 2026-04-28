package org.example.agentaiops.repair.tool;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.CommandResult;
import org.springframework.stereotype.Component;

/** Resolves the GitHub owner/repo for the current workspace, with config override and git remote fallback. */
@Component
public class GitRepoLocator {

    private static final Pattern HTTPS_PATTERN = Pattern.compile(
            "^https?://[^/]+/(?<owner>[^/]+)/(?<repo>[^/.]+)(?:\\.git)?/?$");
    private static final Pattern SSH_PATTERN = Pattern.compile(
            "^git@[^:]+:(?<owner>[^/]+)/(?<repo>[^/.]+)(?:\\.git)?$");

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;
    private final CommandRunner commandRunner;

    public GitRepoLocator(RepairProperties properties, ToolPolicy toolPolicy, CommandRunner commandRunner) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
        this.commandRunner = commandRunner;
    }

    /** Returns the configured owner/repo or auto-detects them from git remote get-url origin. */
    public Optional<RepoCoordinate> locate() {
        String configuredOwner = properties.getGithub().getOwner();
        String configuredRepo = properties.getGithub().getRepo();
        if (hasText(configuredOwner) && hasText(configuredRepo)) {
            return Optional.of(new RepoCoordinate(configuredOwner.trim(), configuredRepo.trim()));
        }
        return parseRemote(originRemoteUrl());
    }

    /** Parses owner/repo out of an HTTPS or SSH GitHub remote URL. */
    static Optional<RepoCoordinate> parseRemote(String remoteUrl) {
        if (remoteUrl == null) {
            return Optional.empty();
        }
        String trimmed = remoteUrl.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        Matcher https = HTTPS_PATTERN.matcher(trimmed);
        if (https.matches()) {
            return Optional.of(new RepoCoordinate(https.group("owner"), https.group("repo")));
        }
        Matcher ssh = SSH_PATTERN.matcher(trimmed);
        if (ssh.matches()) {
            return Optional.of(new RepoCoordinate(ssh.group("owner"), ssh.group("repo")));
        }
        return Optional.empty();
    }

    private String originRemoteUrl() {
        CommandResult result = commandRunner.run(
                toolPolicy.workspaceRoot(),
                List.of("git", "remote", "get-url",
                        hasText(properties.getGit().getRemote()) ? properties.getGit().getRemote() : "origin"),
                Duration.ofSeconds(Math.min(15, properties.getWorkflow().getProcessTimeoutSeconds())));
        if (!result.success()) {
            return null;
        }
        return result.output();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Identifies one GitHub repository by owner and repo name. */
    public record RepoCoordinate(String owner, String repo) {
    }
}
