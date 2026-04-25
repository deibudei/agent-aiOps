package org.example.agentaiops.repair.extension;

import org.example.agentaiops.repair.model.PullRequestResult;

public interface PullRequestProvider {

    PullRequestResult createPullRequest(String branchName, String title, String body);
}
