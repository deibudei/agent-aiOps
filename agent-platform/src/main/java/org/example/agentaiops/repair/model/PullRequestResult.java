package org.example.agentaiops.repair.model;

/** Reports the result of optional GitHub PR creation. */
public record PullRequestResult(boolean success, String url, String message) {
}
