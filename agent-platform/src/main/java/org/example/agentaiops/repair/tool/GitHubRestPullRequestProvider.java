package org.example.agentaiops.repair.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.extension.PullRequestProvider;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Creates a GitHub pull request through the REST API instead of the gh CLI. */
@Component
public class GitHubRestPullRequestProvider implements PullRequestProvider {

    private final RepairProperties properties;
    private final GitRepoLocator gitRepoLocator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** Wires REST API configuration and shared HttpClient. */
    @Autowired
    public GitHubRestPullRequestProvider(
            RepairProperties properties, GitRepoLocator gitRepoLocator, ObjectMapper objectMapper) {
        this(properties, gitRepoLocator, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /** Test-only constructor that injects a custom HttpClient. */
    GitHubRestPullRequestProvider(
            RepairProperties properties,
            GitRepoLocator gitRepoLocator,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.properties = properties;
        this.gitRepoLocator = gitRepoLocator;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public PullRequestResult createPullRequest(String branchName, String title, String body) {
        if (!properties.getGithub().isEnabled()) {
            return new PullRequestResult(true, "", "GitHub PR is disabled; skipped PR creation");
        }
        if (branchName == null || branchName.isBlank()) {
            return new PullRequestResult(false, "", "Branch name is empty");
        }
        String token = properties.getGithub().getToken();
        if (token == null || token.isBlank()) {
            return new PullRequestResult(false, "",
                    "GITHUB_TOKEN is empty; cannot call GitHub REST API");
        }
        Optional<GitRepoLocator.RepoCoordinate> coordinate = gitRepoLocator.locate();
        if (coordinate.isEmpty()) {
            return new PullRequestResult(false, "",
                    "Unable to determine GitHub owner/repo; set repair.github.owner/repo or origin remote");
        }
        String baseBranch = properties.getGit().getBaseBranch();
        if (baseBranch == null || baseBranch.isBlank()) {
            return new PullRequestResult(false, "", "repair.git.base-branch is empty");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(buildPullsUri(coordinate.get()))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token.trim())
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "agent-aiops-repair")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildPayload(branchName, baseBranch, title, body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return interpretResponse(response);
        } catch (JsonProcessingException e) {
            return new PullRequestResult(false, "", "Failed to serialize PR payload: " + e.getMessage());
        } catch (IOException e) {
            return new PullRequestResult(false, "", "GitHub REST request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PullRequestResult(false, "", "Interrupted while calling GitHub REST: " + e.getMessage());
        }
    }

    private URI buildPullsUri(GitRepoLocator.RepoCoordinate coordinate) {
        String base = properties.getGithub().getApiBaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://api.github.com";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/repos/" + coordinate.owner() + "/" + coordinate.repo() + "/pulls");
    }

    private String buildPayload(String head, String base, String title, String body) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("head", head);
        payload.put("base", base);
        payload.put("body", body == null ? "" : body);
        payload.put("maintainer_can_modify", true);
        return objectMapper.writeValueAsString(payload);
    }

    private PullRequestResult interpretResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        if (status == 201) {
            try {
                JsonNode node = objectMapper.readTree(body);
                String url = node.path("html_url").asText("");
                return new PullRequestResult(true, url, "Pull request created");
            } catch (IOException e) {
                return new PullRequestResult(true, "", "Pull request created (response not parseable)");
            }
        }
        if (status == 422 && body.contains("A pull request already exists")) {
            return new PullRequestResult(false, "",
                    "GitHub HTTP 422: " + body);
        }
        return new PullRequestResult(false, "", "GitHub HTTP " + status + ": " + body);
    }
}
