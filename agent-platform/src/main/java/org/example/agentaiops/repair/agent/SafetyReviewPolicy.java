package org.example.agentaiops.repair.agent;

import java.util.List;
import org.example.agentaiops.repair.extension.ReviewPolicy;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.tool.GitTools;
import org.springframework.stereotype.Component;

@Component
public class SafetyReviewPolicy implements ReviewPolicy {

    private final GitTools gitTools;

    public SafetyReviewPolicy(GitTools gitTools) {
        this.gitTools = gitTools;
    }

    @Override
    public boolean allows(TestExecutionResult testResult, List<String> changedFiles) {
        return testResult != null
                && testResult.success()
                && !changedFiles.isEmpty()
                && gitTools.allChangedFilesAllowed(changedFiles);
    }
}
