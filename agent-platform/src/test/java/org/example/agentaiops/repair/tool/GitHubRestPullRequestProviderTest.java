package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.junit.jupiter.api.Test;

class GitHubRestPullRequestProviderTest {

    @Test
    void returnsHtmlUrlOnSuccess() throws IOException, InterruptedException {
        RepairProperties properties = githubProperties("ghp_test");
        properties.getGithub().setEnabled(true);
        GitRepoLocator locator = mock(GitRepoLocator.class);
        when(locator.locate()).thenReturn(Optional.of(new GitRepoLocator.RepoCoordinate("deibudei", "agent-aiOps")));
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn(
                "{\"html_url\":\"https://github.com/deibudei/agent-aiOps/pull/42\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GitHubRestPullRequestProvider provider =
                new GitHubRestPullRequestProvider(properties, locator, new ObjectMapper(), httpClient);

        PullRequestResult result = provider.createPullRequest(
                "repair/session-001", "fix(repair): foo", "body");

        assertThat(result.success()).isTrue();
        assertThat(result.url()).isEqualTo("https://github.com/deibudei/agent-aiOps/pull/42");
    }

    @Test
    void reportsHttpErrorBody() throws IOException, InterruptedException {
        RepairProperties properties = githubProperties("ghp_test");
        properties.getGithub().setEnabled(true);
        GitRepoLocator locator = mock(GitRepoLocator.class);
        when(locator.locate()).thenReturn(Optional.of(new GitRepoLocator.RepoCoordinate("deibudei", "agent-aiOps")));
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"message\":\"Bad credentials\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GitHubRestPullRequestProvider provider =
                new GitHubRestPullRequestProvider(properties, locator, new ObjectMapper(), httpClient);

        PullRequestResult result = provider.createPullRequest(
                "repair/session-002", "fix(repair): foo", "body");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("HTTP 401").contains("Bad credentials");
    }

    @Test
    void skipsWhenGitHubDisabled() {
        RepairProperties properties = githubProperties("ghp_test");
        properties.getGithub().setEnabled(false);
        GitRepoLocator locator = mock(GitRepoLocator.class);
        HttpClient httpClient = mock(HttpClient.class);

        GitHubRestPullRequestProvider provider =
                new GitHubRestPullRequestProvider(properties, locator, new ObjectMapper(), httpClient);

        PullRequestResult result = provider.createPullRequest("repair/session-x", "title", "body");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("disabled");
    }

    @Test
    void failsWhenTokenMissing() {
        RepairProperties properties = githubProperties("");
        properties.getGithub().setEnabled(true);
        GitRepoLocator locator = mock(GitRepoLocator.class);
        HttpClient httpClient = mock(HttpClient.class);

        GitHubRestPullRequestProvider provider =
                new GitHubRestPullRequestProvider(properties, locator, new ObjectMapper(), httpClient);

        PullRequestResult result = provider.createPullRequest("repair/session-x", "title", "body");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("GITHUB_TOKEN");
    }

    private RepairProperties githubProperties(String token) {
        RepairProperties properties = new RepairProperties();
        properties.getGithub().setToken(token);
        properties.getGithub().setClient("rest");
        properties.getGit().setBaseBranch("demo/fault/quantity-division-by-zero");
        return properties;
    }
}
