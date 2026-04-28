package org.example.agentaiops.repair.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairTiming;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Sends an interactive Feishu card after a repair run. */
@Component
public class FeishuTools {

    private final RepairProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** Wires Feishu configuration and JSON serialization. */
    @Autowired
    public FeishuTools(RepairProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** Test-only constructor that injects a custom HttpClient. */
    FeishuTools(RepairProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /** Sends a v2 interactive card with repair target, PR link, timing, and token usage. */
    public NotificationResult sendRepairCard(
            String sessionId,
            String repairTarget,
            String rootCause,
            String reviewReason,
            PullRequestResult pullRequestResult,
            RepairTiming timing) {
        if (!properties.getFeishu().isEnabled()) {
            return new NotificationResult(true, "Feishu is disabled; skipped notification");
        }
        String webhookUrl = properties.getFeishu().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return new NotificationResult(false, "Feishu webhook URL is empty");
        }

        try {
            String payload = buildPayload(sessionId, repairTarget, rootCause, reviewReason, pullRequestResult, timing);
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new NotificationResult(true, "Feishu card sent");
            }
            return new NotificationResult(false,
                    "Feishu returned HTTP " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            return new NotificationResult(false, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new NotificationResult(false, "Interrupted: " + e.getMessage());
        }
    }

    /** Builds the v2 interactive Feishu card payload. */
    private String buildPayload(
            String sessionId,
            String repairTarget,
            String rootCause,
            String reviewReason,
            PullRequestResult pullRequestResult,
            RepairTiming timing) throws JsonProcessingException {
        String prUrl = pullRequestResult == null ? "" : pullRequestResult.url();
        String prMessage = pullRequestResult == null ? "" : pullRequestResult.message();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", Map.of(
                "template", "green",
                "title", Map.of("tag", "plain_text", "content", "自动修复完成 · 请 Review")));

        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(Map.of(
                "tag", "div",
                "text", Map.of(
                        "tag", "lark_md",
                        "content", buildSummaryText(sessionId, repairTarget, rootCause, reviewReason, prUrl, prMessage))));
        elements.add(Map.of(
                "tag", "div",
                "text", Map.of(
                        "tag", "lark_md",
                        "content", buildTimingText(timing))));
        elements.add(Map.of(
                "tag", "action",
                "actions", buildButtons(prUrl, sessionId)));
        elements.add(Map.of(
                "tag", "note",
                "elements", List.of(Map.of(
                        "tag", "plain_text",
                        "content", "agent-aiops · session=" + sessionId))));
        card.put("elements", elements);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msg_type", "interactive");
        payload.put("card", card);

        String signingSecret = properties.getFeishu().getSigningSecret();
        if (signingSecret != null && !signingSecret.isBlank()) {
            long timestamp = System.currentTimeMillis() / 1000L;
            payload.put("timestamp", String.valueOf(timestamp));
            payload.put("sign", sign(timestamp, signingSecret));
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String buildSummaryText(
            String sessionId,
            String repairTarget,
            String rootCause,
            String reviewReason,
            String prUrl,
            String prMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("**我发现了一个 Bug 并已为您修复，请 Review。**\n");
        sb.append("- 修复目标：").append(emptyDash(repairTarget)).append('\n');
        sb.append("- 根因：").append(emptyDash(rootCause)).append('\n');
        sb.append("- Review：").append(emptyDash(reviewReason)).append('\n');
        if (prUrl != null && !prUrl.isBlank()) {
            sb.append("- PR：[").append(prUrl).append("](").append(prUrl).append(")\n");
        } else {
            sb.append("- PR：").append(emptyDash(prMessage)).append('\n');
        }
        sb.append("- Session：`").append(sessionId).append('`');
        return sb.toString();
    }

    private String buildTimingText(RepairTiming timing) {
        if (timing == null) {
            return "Timing: n/a";
        }
        long durationMillis = timing.durationMillis();
        double durationSeconds = durationMillis / 1000.0;
        long inputTokens = sumTokens(timing, RepairModelUsage::inputTokenCount);
        long outputTokens = sumTokens(timing, RepairModelUsage::outputTokenCount);
        long totalTokens = sumTokens(timing, RepairModelUsage::totalTokenCount);
        return String.format(
                "**耗时与 Token**\n- 总耗时：%.2fs\n- Token：input %d / output %d / total %d",
                durationSeconds, inputTokens, outputTokens, totalTokens);
    }

    private List<Map<String, Object>> buildButtons(String prUrl, String sessionId) {
        List<Map<String, Object>> buttons = new ArrayList<>();
        if (prUrl != null && !prUrl.isBlank()) {
            buttons.add(Map.of(
                    "tag", "button",
                    "type", "primary",
                    "text", Map.of("tag", "plain_text", "content", "查看 PR"),
                    "url", prUrl));
        }
        buttons.add(Map.of(
                "tag", "button",
                "type", "default",
                "text", Map.of("tag", "plain_text", "content", "查看修复记录"),
                "url", "https://github.com/search?q=" + sessionId));
        return buttons;
    }

    private long sumTokens(
            RepairTiming timing,
            java.util.function.Function<RepairModelUsage, Integer> selector) {
        if (timing == null || timing.modelUsage() == null) {
            return 0L;
        }
        long total = 0L;
        for (RepairModelUsage usage : timing.modelUsage()) {
            Integer value = selector.apply(usage);
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private String emptyDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String sign(long timestamp, String signingSecret) {
        try {
            String stringToSign = timestamp + "\n" + signingSecret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(new byte[0]);
            return java.util.Base64.getEncoder().encodeToString(signData);
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            return "";
        }
    }
}
