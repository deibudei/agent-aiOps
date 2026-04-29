package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairTiming;
import org.junit.jupiter.api.Test;

class FeishuToolsTest {

    @Test
    void sendsSuccessAndFailureCardsWithDifferentCopy() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RepairProperties properties = new RepairProperties();
            properties.getFeishu().setEnabled(true);
            properties.getFeishu().setWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            FeishuTools tools = new FeishuTools(properties, new ObjectMapper());

            tools.sendRepairCard(
                    "session-ok",
                    RepairOutcome.FIXED,
                    "PR created",
                    "target",
                    "root",
                    "review pass",
                    new PullRequestResult(true, "https://github.com/o/r/pull/1", "Pull request created"),
                    null);
            tools.sendRepairCard(
                    "session-failed",
                    RepairOutcome.FAILED,
                    "tests still failing",
                    "target",
                    "root",
                    "review revise",
                    new PullRequestResult(false, "", "PR skipped"),
                    null);
        } finally {
            server.stop(0);
        }

        assertThat(bodies.get(0)).contains("我发现了一个 Bug 并已为您修复");
        assertThat(bodies.get(0)).contains("https://github.com/o/r/pull/1");
        assertThat(bodies.get(1)).contains("自动修复未完成");
        assertThat(bodies.get(1)).contains("tests still failing");
        assertThat(bodies.get(1)).doesNotContain("我发现了一个 Bug 并已为您修复");
    }

    @Test
    void rendersUnknownTokenUsageAsUnknownInsteadOfZero() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RepairProperties properties = new RepairProperties();
            properties.getFeishu().setEnabled(true);
            properties.getFeishu().setWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            FeishuTools tools = new FeishuTools(properties, new ObjectMapper());

            Instant startedAt = Instant.parse("2026-04-29T08:00:00Z");
            RepairTiming noModelUsage =
                    new RepairTiming(startedAt, startedAt.plusSeconds(2), 2000, List.of(), List.of());
            RepairTiming noTokenUsage = new RepairTiming(
                    startedAt,
                    startedAt.plusSeconds(3),
                    3000,
                    List.of(),
                    List.of(new RepairModelUsage("diagnoseRootCause", "DIAGNOSIS", "model-a", "model-a",
                            2, null, null, null)));

            tools.sendRepairCard(
                    "session-no-usage",
                    RepairOutcome.FIXED,
                    "PR created",
                    "target",
                    "root",
                    "review pass",
                    new PullRequestResult(true, "https://github.com/o/r/pull/1", "Pull request created"),
                    noModelUsage);
            tools.sendRepairCard(
                    "session-no-token",
                    RepairOutcome.FIXED,
                    "PR created",
                    "target",
                    "root",
                    "review pass",
                    new PullRequestResult(true, "https://github.com/o/r/pull/1", "Pull request created"),
                    noTokenUsage);
        } finally {
            server.stop(0);
        }

        assertThat(bodies.get(0)).contains("未采集到模型 usage");
        assertThat(bodies.get(0)).doesNotContain("total 0");
        assertThat(bodies.get(1)).contains("provider 未返回 token usage");
        assertThat(bodies.get(1)).contains("model calls 2");
        assertThat(bodies.get(1)).doesNotContain("total 0");
    }
}
