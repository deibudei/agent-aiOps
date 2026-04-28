package org.example.agentaiops.repair.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.extension.PullRequestProvider;
import org.example.agentaiops.repair.model.CommandResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.springframework.stereotype.Component;

/** Routes pull-request creation to either the REST API or the gh CLI based on configuration. */
@Component
public class GitHubTools implements PullRequestProvider {

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;
    private final CommandRunner commandRunner;
    private final GitHubRestPullRequestProvider restProvider;

    /** Wires GitHub flags, repository policy, command execution, and the REST provider. */
    public GitHubTools(
            RepairProperties properties,
            ToolPolicy toolPolicy,
            CommandRunner commandRunner,
            GitHubRestPullRequestProvider restProvider) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
        this.commandRunner = commandRunner;
        this.restProvider = restProvider;
    }

    @Override
    public PullRequestResult createPullRequest(String branchName, String title, String body) {
        if (!properties.getGithub().isEnabled()) {
            return new PullRequestResult(true, "", "GitHub PR is disabled; skipped PR creation");
        }
        if (branchName == null || branchName.isBlank()) {
            return new PullRequestResult(false, "", "Branch name is empty");
        }
        return switch (clientName()) {
            case "rest" -> restProvider.createPullRequest(branchName, title, body);
            case "cli" -> createViaCli(branchName, title, body);
            default -> new PullRequestResult(false, "", "Unsupported repair.github.client: "
                    + properties.getGithub().getClient());
        };
    }

    private PullRequestResult createViaCli(String branchName, String title, String body) {
        List<String> command = new ArrayList<>();
        command.add("gh");
        command.add("pr");
        command.add("create");
        command.add("--title");
        command.add(title);
        command.add("--body");
        command.add(body);
        command.add("--base");
        command.add(properties.getGit().getBaseBranch());
        command.add("--head");
        command.add(branchName);

        CommandResult result = commandRunner.run(
                toolPolicy.workspaceRoot(),
                command,
                Duration.ofSeconds(properties.getWorkflow().getProcessTimeoutSeconds()));

        if (!result.success()) {
            return new PullRequestResult(false, "", result.output());
        }
        return new PullRequestResult(true, result.output().trim(), "Pull request created");
    }

    private String clientName() {
        String client = properties.getGithub().getClient();
        return client == null ? "rest" : client.trim().toLowerCase(Locale.ROOT);
    }
}
