package org.example.agentaiops.repair.agent;

import java.util.List;
import org.example.agentaiops.repair.extension.ReviewPolicy;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.tool.GitTools;
import org.springframework.stereotype.Component;

@Component
public class SafetyReviewPolicy implements ReviewPolicy {

    private final GitTools gitTools;

    /** Uses git file checks as the final safety boundary. */
    public SafetyReviewPolicy(GitTools gitTools) {
        this.gitTools = gitTools;
    }

    /** Allows only passing tests with changes inside the target-service whitelist. */
    @Override
    public boolean allows(TestExecutionResult testResult, List<String> changedFiles) {
        return testResult != null
                && testResult.success()
                && !changedFiles.isEmpty()
                && gitTools.allChangedFilesAllowed(changedFiles);
    }
}
