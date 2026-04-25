package org.example.agentaiops.repair.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.extension.PullRequestProvider;
import org.example.agentaiops.repair.model.CommandResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.springframework.stereotype.Component;

@Component
public class GitHubTools implements PullRequestProvider {

    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;
    private final CommandRunner commandRunner;

    public GitHubTools(RepairProperties properties, ToolPolicy toolPolicy, CommandRunner commandRunner) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
        this.commandRunner = commandRunner;
    }

    @Override
    public PullRequestResult createPullRequest(String branchName, String title, String body) {
        if (!properties.getGithub().isEnabled()) {
            return new PullRequestResult(true, "", "GitHub PR is disabled; skipped PR creation");
        }
        if (branchName == null || branchName.isBlank()) {
            return new PullRequestResult(false, "", "Branch name is empty");
        }

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
}
