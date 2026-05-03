package org.example.agentaiops.demo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DemoTargetServiceRestarter {

    private static final DateTimeFormatter LOG_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Shanghai"));

    private final Map<String, Process> managedProcesses = new ConcurrentHashMap<>();
    private final RepairProperties properties;
    private final ToolPolicy toolPolicy;
    private final HttpClient httpClient;

    /** Wires controlled process startup for PR-safe demo worktrees. */
    @Autowired
    public DemoTargetServiceRestarter(RepairProperties properties, ToolPolicy toolPolicy) {
        this(properties, toolPolicy, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    DemoTargetServiceRestarter(RepairProperties properties, ToolPolicy toolPolicy, HttpClient httpClient) {
        this.properties = properties;
        this.toolPolicy = toolPolicy;
        this.httpClient = httpClient;
    }

    /** Restarts target-service from the scenario worktree and waits until it accepts HTTP requests. */
    public DemoTargetRestartResult restart(DemoScenarioResult scenario) {
        String sessionId = scenario.sessionId();
        Path worktree = Path.of(scenario.worktreePath()).toAbsolutePath().normalize();
        String commandText = mavenCommand() + " -pl target-service spring-boot:run";
        try {
            validateWorktree(worktree);
            stopManagedProcess(sessionId);
            stopProcessOnTargetPort();

            Path logPath = worktree.resolve("target-service/logs/auto-restart-"
                    + safeFileName(sessionId) + "-" + LOG_TIMESTAMP.format(Instant.now()) + ".log");
            Files.createDirectories(logPath.getParent());
            Process process = startTargetService(worktree, logPath);
            managedProcesses.put(sessionId, process);

            if (!waitUntilTargetResponds(process, Duration.ofSeconds(60))) {
                process.destroyForcibly();
                managedProcesses.remove(sessionId);
                return failure(
                        scenario,
                        commandText,
                        null,
                        toolPolicy.display(logPath),
                        "target-service did not become reachable on " + targetServiceBaseUrl()
                                + ". Check " + toolPolicy.display(logPath) + ".");
            }

            return new DemoTargetRestartResult(
                    sessionId,
                    true,
                    "target-service restarted from " + scenario.worktreePath(),
                    scenario.worktreePath(),
                    commandText,
                    process.pid(),
                    toolPolicy.display(logPath),
                    List.of("POST /api/demo/pr-scenarios/" + sessionId + "/confirm-target-restarted"));
        } catch (IOException | IllegalArgumentException e) {
            return failure(scenario, commandText, null, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(scenario, commandText, null, "", "Interrupted while restarting target-service");
        }
    }

    private Process startTargetService(Path worktree, Path logPath) throws IOException {
        return new ProcessBuilder(mavenCommand(), "-pl", "target-service", "spring-boot:run")
                .directory(worktree.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()))
                .start();
    }

    private void validateWorktree(Path worktree) {
        Path workspaceRoot = toolPolicy.homeWorkspaceRoot().toAbsolutePath().normalize();
        if (worktree.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Refusing to auto-restart target-service from the main checkout: "
                    + worktree);
        }
        if (!Files.isRegularFile(worktree.resolve("pom.xml"))
                || !Files.isRegularFile(worktree.resolve("target-service/pom.xml"))) {
            throw new IllegalArgumentException("PR-safe worktree is not a valid project root: " + worktree);
        }
    }

    private void stopManagedProcess(String sessionId) {
        Process previous = managedProcesses.remove(sessionId);
        if (previous != null && previous.isAlive()) {
            previous.destroy();
            try {
                if (!previous.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    previous.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                previous.destroyForcibly();
            }
        }
    }

    private void stopProcessOnTargetPort() {
        int port = targetPort();
        if (port <= 0) {
            return;
        }
        List<String> command = isWindows()
                ? List.of(
                        "powershell.exe",
                        "-NoProfile",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-Command",
                        "$items=Get-NetTCPConnection -LocalPort " + port
                                + " -State Listen -ErrorAction SilentlyContinue; "
                                + "foreach($item in $items){ Stop-Process -Id $item.OwningProcess -Force -ErrorAction SilentlyContinue }")
                : List.of(
                        "sh",
                        "-c",
                        "pids=$(lsof -ti tcp:" + port + " 2>/dev/null); "
                                + "if [ -n \"$pids\" ]; then kill -9 $pids; fi");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean waitUntilTargetResponds(Process process, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                return false;
            }
            if (targetResponds()) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private boolean targetResponds() {
        String url = targetServiceBaseUrl() + "/api/orders/quote?totalCents=100&quantity=1";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() > 0;
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private DemoTargetRestartResult failure(
            DemoScenarioResult scenario,
            String command,
            Long pid,
            String logPath,
            String message) {
        return new DemoTargetRestartResult(
                scenario.sessionId(),
                false,
                message,
                scenario.worktreePath(),
                command,
                pid,
                logPath,
                List.of(
                        "Check whether another process is holding " + targetServiceBaseUrl() + ".",
                        "If needed, restart target-service manually from " + scenario.worktreePath() + "."));
    }

    private int targetPort() {
        try {
            URI uri = URI.create(targetServiceBaseUrl());
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private String targetServiceBaseUrl() {
        String baseUrl = properties.getTargetProject().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:9910";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String mavenCommand() {
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String safeFileName(String value) {
        return value == null ? "session" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
