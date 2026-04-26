package org.example.agentaiops.repair.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.springframework.stereotype.Component;

@Component
public class FeishuTools {

    private final RepairProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** Wires Feishu configuration and JSON serialization. */
    public FeishuTools(RepairProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Sends a repair review card to Feishu when notification is enabled. */
    public NotificationResult sendRepairCard(
            String sessionId, PullRequestResult pullRequestResult, String rootCause, String fixStrategy) {
        if (!properties.getFeishu().isEnabled()) {
            return new NotificationResult(true, "Feishu is disabled; skipped notification");
        }
        String webhookUrl = properties.getFeishu().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return new NotificationResult(false, "Feishu webhook URL is empty");
        }

        try {
            String payload = buildPayload(sessionId, pullRequestResult, rootCause, fixStrategy);
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new NotificationResult(true, "Feishu card sent");
            }
            return new NotificationResult(false, "Feishu returned HTTP " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            return new NotificationResult(false, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new NotificationResult(false, "Interrupted: " + e.getMessage());
        }
    }

    /** Builds the interactive Feishu card payload. */
    private String buildPayload(
            String sessionId, PullRequestResult pullRequestResult, String rootCause, String fixStrategy)
            throws JsonProcessingException {
        String prText = pullRequestResult.url() == null || pullRequestResult.url().isBlank()
                ? pullRequestResult.message()
                : pullRequestResult.url();
        String content = "**我发现了一个 Bug 并已为您修复，请 Review**\n"
                + "- Session: " + sessionId + "\n"
                + "- Root cause: " + rootCause + "\n"
                + "- Fix: " + fixStrategy + "\n"
                + "- PR: " + prText;

        Map<String, Object> payload = Map.of(
                "msg_type", "interactive",
                "card", Map.of(
                        "header", Map.of(
                                "title", Map.of("tag", "plain_text", "content", "自动修复完成")),
                        "elements", List.of(Map.of(
                                "tag", "div",
                                "text", Map.of("tag", "lark_md", "content", content)))));
        return objectMapper.writeValueAsString(payload);
    }
}
