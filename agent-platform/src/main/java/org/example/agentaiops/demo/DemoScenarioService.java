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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairRunResponse;
import org.example.agentaiops.repair.model.RepairWorktreeResult;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.service.RepairWorkflowService;
import org.example.agentaiops.repair.tool.GitTools;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.example.agentaiops.repair.tool.RepairWorkspaceContext;
import org.example.agentaiops.repair.tool.RunTestTools;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DemoScenarioService {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,80}");
    private static final ZoneId TRACEBACK_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(TRACEBACK_ZONE);
    private static final DateTimeFormatter CONTENT_TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Map<String, DemoScenarioResult> scenarios = new ConcurrentHashMap<>();
    private final Map<String, DemoScenarioResult> pullRequestScenarios = new ConcurrentHashMap<>();
    private final DemoFaultService demoFaultService;
    private final RepairWorkflowService repairWorkflowService;
    private final RunTestTools runTestTools;
    private final ToolPolicy toolPolicy;
    private final RepairProperties properties;
    private final GitTools gitTools;
    private final RepairRecordTools repairRecordTools;
    private final RepairWorkspaceContext workspaceContext;
    private final HttpClient httpClient;

    /** Wires the local one-click demo scenario orchestration service. */
    @Autowired
    public DemoScenarioService(
            DemoFaultService demoFaultService,
            RepairWorkflowService repairWorkflowService,
            RunTestTools runTestTools,
            ToolPolicy toolPolicy,
            GitTools gitTools,
            RepairRecordTools repairRecordTools,
            RepairWorkspaceContext workspaceContext,
            RepairProperties properties) {
        this(
                demoFaultService,
                repairWorkflowService,
                runTestTools,
                toolPolicy,
                gitTools,
                repairRecordTools,
                workspaceContext,
                properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    DemoScenarioService(
            DemoFaultService demoFaultService,
            RepairWorkflowService repairWorkflowService,
            RunTestTools runTestTools,
            ToolPolicy toolPolicy,
            GitTools gitTools,
            RepairRecordTools repairRecordTools,
            RepairWorkspaceContext workspaceContext,
            RepairProperties properties,
            HttpClient httpClient) {
        this.demoFaultService = demoFaultService;
        this.repairWorkflowService = repairWorkflowService;
        this.runTestTools = runTestTools;
        this.toolPolicy = toolPolicy;
        this.gitTools = gitTools;
        this.repairRecordTools = repairRecordTools;
        this.workspaceContext = workspaceContext;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** Starts a source-level demo scenario and waits for the operator to restart target-service. */
    public DemoScenarioResult start(DemoScenarioStartRequest request) {
        String faultType = request == null ? "" : request.getFaultType();
        DemoFaultType type = parseFaultType(faultType);
        String sessionId = requestedSessionId(request);
        if (scenarios.containsKey(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demo scenario already exists: " + sessionId);
        }
        if (properties.getGit().isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Source-injection demo scenarios require REPAIR_GIT_ENABLED=false. "
                            + "For a real PR demo, use the committed demo/fault base branch and POST /api/repair/run.");
        }

        DemoFaultResult injected = demoFaultService.inject(type.wireName());
        DemoScenarioResult result;
        if (injected.success()) {
            result = new DemoScenarioResult(
                    sessionId,
                    type.wireName(),
                    DemoScenarioStage.WAITING_FOR_TARGET_RESTART,
                    true,
                    "Demo fault injected. Restart target-service, then confirm the scenario.",
                    injected.changedFiles(),
                    waitingNextSteps(sessionId),
                    null,
                    targetServiceBaseUrl(),
                    "",
                    "",
                    "",
                    "",
                    null,
                    "",
                    "",
                    "");
        } else {
            result = new DemoScenarioResult(
                    sessionId,
                    type.wireName(),
                    DemoScenarioStage.FAILED,
                    false,
                    injected.message(),
                    injected.changedFiles(),
                    injected.nextSteps(),
                    null,
                    targetServiceBaseUrl(),
                    "",
                    "",
                    "",
                    "",
                    null,
                    "",
                    "",
                    "");
        }

        DemoScenarioResult previous = scenarios.putIfAbsent(sessionId, result);
        if (previous != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demo scenario already exists: " + sessionId);
        }
        return result;
    }

    /** Returns the current state for a demo scenario. */
    public DemoScenarioResult get(String sessionId) {
        String safeSessionId = validateSessionId(sessionId);
        DemoScenarioResult result = scenarios.get(safeSessionId);
        if (result != null) {
            return refreshFromRecord(scenarios, safeSessionId, result);
        }
        result = pullRequestScenarios.get(safeSessionId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo scenario not found: " + sessionId);
        }
        return refreshFromRecord(pullRequestScenarios, safeSessionId, result);
    }

    /** Confirms the manual restart, prepares fresh evidence, and starts the repair workflow. */
    public DemoScenarioResult confirmTargetRestarted(String sessionId) {
        String safeSessionId = validateSessionId(sessionId);
        DemoScenarioResult current = get(safeSessionId);
        if (current.stage() != DemoScenarioStage.WAITING_FOR_TARGET_RESTART) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Demo scenario is not waiting for target-service restart: " + safeSessionId);
        }

        DemoFaultType type = parseFaultType(current.faultType());
        ScenarioEvidence evidence = prepareEvidence(safeSessionId, type);
        if (!evidence.success()) {
            DemoScenarioResult failed = copy(
                    current,
                    DemoScenarioStage.WAITING_FOR_TARGET_RESTART,
                    false,
                    evidence.message(),
                    evidence.nextSteps(),
                    null,
                    evidence.summary());
            scenarios.put(safeSessionId, failed);
            return failed;
        }

        RepairRunResponse repair = repairWorkflowService.startAsync(safeSessionId);
        DemoScenarioResult started = copy(
                current,
                DemoScenarioStage.RUNNING,
                true,
                "Evidence prepared and repair workflow started.",
                List.of(
                        "Open " + repair.streamUrl() + " for SSE repair progress.",
                        "Wait for repair-records/" + safeSessionId + ".json and .md.",
                        "Use GET /api/repair/records to inspect the experiment summary."),
                repair.streamUrl(),
                evidence.summary());
        scenarios.put(safeSessionId, started);
        return started;
    }

    /** Prepares a PR-safe scenario from the configured committed fault base branch. */
    public DemoScenarioResult startPullRequestScenario(DemoScenarioStartRequest request) {
        String faultType = request == null ? "" : request.getFaultType();
        DemoFaultType type = parseFaultType(faultType);
        String sessionId = requestedSessionId(request);
        if (scenarios.containsKey(sessionId) || pullRequestScenarios.containsKey(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demo scenario already exists: " + sessionId);
        }
        if (!properties.getGit().isEnabled() || !properties.getGithub().isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PR demo scenarios require REPAIR_GIT_ENABLED=true and REPAIR_GITHUB_ENABLED=true");
        }
        String expectedBaseBranch = "demo/fault/" + type.wireName();
        String configuredBaseBranch = properties.getGit().getBaseBranch();
        if (!expectedBaseBranch.equals(configuredBaseBranch)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Set REPAIR_BASE_BRANCH=" + expectedBaseBranch
                            + " before starting this PR demo scenario. Current base branch is "
                            + configuredBaseBranch);
        }

        RepairWorktreeResult prepared = gitTools.prepareRepairWorktreeFromBase(sessionId);
        if (!prepared.success()) {
            DemoScenarioResult failed = new DemoScenarioResult(
                    sessionId,
                    type.wireName(),
                    DemoScenarioStage.FAILED,
                    false,
                    prepared.message(),
                    List.of(),
                    List.of(
                            "Use a new sessionId if this worktree already exists.",
                            "Ensure " + configuredBaseBranch + " exists locally or on the configured remote."),
                    null,
                    targetServiceBaseUrl(),
                    "",
                    prepared.branchName(),
                    prepared.worktreePath(),
                    "",
                    null,
                    "",
                    "",
                    "");
            pullRequestScenarios.put(sessionId, failed);
            return failed;
        }

        DemoScenarioResult waiting = new DemoScenarioResult(
                sessionId,
                type.wireName(),
                DemoScenarioStage.WAITING_FOR_TARGET_RESTART,
                true,
                "Prepared " + prepared.branchName()
                        + " from " + configuredBaseBranch
                        + " in " + prepared.worktreePath()
                        + ". Restart target-service from that worktree, then confirm the scenario.",
                List.of(),
                waitingPullRequestNextSteps(sessionId, prepared.worktreePath()),
                null,
                targetServiceBaseUrl(),
                "branch=" + prepared.branchName() + ", base=" + configuredBaseBranch,
                prepared.branchName(),
                prepared.worktreePath(),
                "",
                null,
                "",
                "",
                "");
        pullRequestScenarios.put(sessionId, waiting);
        return waiting;
    }

    /** Returns read-only readiness information for a PR-safe demo scenario. */
    public DemoPrScenarioReadiness pullRequestReadiness(String faultType) {
        DemoFaultType type = parseFaultType(faultType);
        String expectedBaseBranch = "demo/fault/" + type.wireName();
        String configuredBaseBranch = properties.getGit().getBaseBranch();
        boolean baseBranchMatches = expectedBaseBranch.equals(configuredBaseBranch);
        boolean llmEnabled = properties.getLlm().isEnabled();
        boolean gitEnabled = properties.getGit().isEnabled();
        boolean githubEnabled = properties.getGithub().isEnabled();
        boolean feishuEnabled = properties.getFeishu().isEnabled();
        String worktreeRoot = properties.getGit().getWorktreeRoot();

        List<String> warnings = new ArrayList<>();
        if (!llmEnabled) {
            warnings.add("Set REPAIR_LLM_ENABLED=true before running the real demo.");
        }
        if (!gitEnabled) {
            warnings.add("Set REPAIR_GIT_ENABLED=true so the Agent can create a repair branch.");
        }
        if (!githubEnabled) {
            warnings.add("Set REPAIR_GITHUB_ENABLED=true so the Agent can create a GitHub PR.");
        }
        if (githubEnabled && !hasText(properties.getGithub().getToken())) {
            warnings.add("GITHUB_TOKEN is empty; GitHub REST PR creation will fail.");
        }
        if (!feishuEnabled) {
            warnings.add("Set FEISHU_ENABLED=true for the competition Feishu notification step.");
        }
        if (feishuEnabled && !hasText(properties.getFeishu().getWebhookUrl())) {
            warnings.add("FEISHU_WEBHOOK_URL is empty; Feishu notification will fail.");
        }
        if (!baseBranchMatches) {
            warnings.add("Set REPAIR_BASE_BRANCH=" + expectedBaseBranch
                    + " for the selected fault. Current value is " + configuredBaseBranch + ".");
        }
        if (!hasText(worktreeRoot)) {
            warnings.add("Set REPAIR_WORKTREE_ROOT to a directory outside the main checkout.");
        }

        return new DemoPrScenarioReadiness(
                type.wireName(),
                expectedBaseBranch,
                configuredBaseBranch,
                baseBranchMatches,
                llmEnabled,
                gitEnabled,
                githubEnabled,
                feishuEnabled,
                worktreeRoot,
                warnings.isEmpty(),
                List.copyOf(warnings));
    }

    /** Confirms target-service restart and starts the repair workflow for a PR-safe scenario. */
    public DemoScenarioResult confirmPullRequestTargetRestarted(String sessionId) {
        String safeSessionId = validateSessionId(sessionId);
        DemoScenarioResult current = pullRequestScenarios.get(safeSessionId);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PR demo scenario not found: " + safeSessionId);
        }
        if (current.stage() != DemoScenarioStage.WAITING_FOR_TARGET_RESTART) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PR demo scenario is not waiting for target-service restart: " + safeSessionId);
        }

        Path worktreeRoot = parseWorktreePath(current);
        return workspaceContext.callWithWorkspace(worktreeRoot, () -> confirmPullRequestInWorkspace(safeSessionId, current));
    }

    private DemoScenarioResult confirmPullRequestInWorkspace(String safeSessionId, DemoScenarioResult current) {
        DemoFaultType type = parseFaultType(current.faultType());
        ScenarioEvidence evidence = prepareEvidence(safeSessionId, type);
        if (!evidence.success()) {
            DemoScenarioResult failed = copy(
                    current,
                    DemoScenarioStage.WAITING_FOR_TARGET_RESTART,
                    false,
                    evidence.message(),
                    evidence.nextSteps(),
                    null,
                    evidence.summary());
            pullRequestScenarios.put(safeSessionId, failed);
            return failed;
        }

        RepairRunResponse repair = repairWorkflowService.startAsync(safeSessionId, current.worktreePath());
        DemoScenarioResult started = copy(
                current,
                DemoScenarioStage.RUNNING,
                true,
                "Evidence prepared and PR repair workflow started.",
                List.of(
                        "Open " + repair.streamUrl() + " for SSE repair progress.",
                        "Wait for GitHub PR creation and Feishu notification.",
                        "Use GET /api/repair/records to inspect the experiment summary."),
                repair.streamUrl(),
                evidence.summary());
        pullRequestScenarios.put(safeSessionId, started);
        return started;
    }

    private ScenarioEvidence prepareEvidence(String sessionId, DemoFaultType type) {
        return switch (type) {
            case QUANTITY_DIVISION_BY_ZERO -> triggerQuantityRuntimeFailure();
            case WRONG_QUOTE_ROUTE, WRONG_ERROR_STATUS -> writeLatestTestFailureEvidence(sessionId, type);
        };
    }

    private ScenarioEvidence triggerQuantityRuntimeFailure() {
        String url = targetServiceBaseUrl() + "/api/orders/quote?totalCents=100&quantity=0";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) {
                return ScenarioEvidence.success(
                        "Triggered target-service runtime failure at " + url
                                + " with HTTP " + response.statusCode());
            }
            return ScenarioEvidence.failure(
                    "Expected target-service to return HTTP 500 after restart, but got HTTP "
                            + response.statusCode() + ". The target-service process may still be running fixed code.",
                    List.of(
                            "Restart target-service so the injected source-level fault is loaded.",
                            "Call POST /api/demo/scenarios/{sessionId}/confirm-target-restarted again."));
        } catch (IOException e) {
            return ScenarioEvidence.failure(
                    "Could not call target-service at " + url + ": " + e.getMessage(),
                    List.of("Start or restart target-service on " + targetServiceBaseUrl() + "."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScenarioEvidence.failure("Interrupted while calling target-service: " + e.getMessage(), List.of());
        } catch (IllegalArgumentException e) {
            return ScenarioEvidence.failure("Invalid target-service URL: " + url, List.of());
        }
    }

    private ScenarioEvidence writeLatestTestFailureEvidence(String sessionId, DemoFaultType type) {
        TestExecutionResult result = runTestTools.runTargetServiceTests();
        if (result.success()) {
            return ScenarioEvidence.failure(
                    "Target-service tests passed after fault injection; the fault may not be active.",
                    List.of(
                            "Check injected source files.",
                            "Restart target-service if you also want HTTP behavior to reflect the fault."));
        }
        try {
            Path evidencePath = writeTestEvidenceLog(sessionId, type, result);
            return ScenarioEvidence.success("Wrote latest test-failure evidence: " + toolPolicy.display(evidencePath));
        } catch (IOException | IllegalArgumentException e) {
            return ScenarioEvidence.failure("Failed to write test-failure evidence: " + e.getMessage(), List.of());
        }
    }

    private Path writeTestEvidenceLog(String sessionId, DemoFaultType type, TestExecutionResult result)
            throws IOException {
        Path tracebacksDir = toolPolicy.targetLogsRoot().resolve("tracebacks").normalize();
        Files.createDirectories(tracebacksDir);
        Instant timestamp = Instant.now();
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        Path path = tracebacksDir.resolve("traceback-%s-%s.log".formatted(
                FILE_TIMESTAMP.format(timestamp), traceId));
        Files.writeString(path, formatTestEvidence(timestamp, traceId, sessionId, type, result));
        return path;
    }

    private String formatTestEvidence(
            Instant timestamp,
            String traceId,
            String sessionId,
            DemoFaultType type,
            TestExecutionResult result) {
        String failure = failureSummary(type);
        return """
                timestamp=%s
                traceId=%s
                scenarioSession=%s
                faultType=%s
                exception=java.lang.AssertionError: %s

                java.lang.AssertionError: %s
                %s

                Target-service test command failed.
                exitCode=%d
                durationMillis=%d

                stdout:
                %s

                stderr:
                %s
                """.formatted(
                CONTENT_TIMESTAMP.format(OffsetDateTime.ofInstant(timestamp, TRACEBACK_ZONE)),
                traceId,
                sessionId,
                type.wireName(),
                failure,
                failure,
                syntheticApplicationFrame(type),
                result.exitCode(),
                result.durationMillis(),
                trim(result.stdout(), 6000),
                trim(result.stderr(), 2000));
    }

    private String failureSummary(DemoFaultType type) {
        return switch (type) {
            case WRONG_QUOTE_ROUTE -> "GET /api/orders/quote returned 404 because OrderController mapping drifted";
            case WRONG_ERROR_STATUS -> "quantity validation returned HTTP 500 instead of HTTP 400 in GlobalExceptionHandler";
            case QUANTITY_DIVISION_BY_ZERO -> "quantity=0 triggered ArithmeticException";
        };
    }

    private String syntheticApplicationFrame(DemoFaultType type) {
        return switch (type) {
            case WRONG_QUOTE_ROUTE ->
                    "\tat com.example.targetservice.controller.OrderController.quote(OrderController.java:22)";
            case WRONG_ERROR_STATUS ->
                    "\tat com.example.targetservice.web.GlobalExceptionHandler.handleIllegalArgument(GlobalExceptionHandler.java:24)";
            case QUANTITY_DIVISION_BY_ZERO ->
                    "\tat com.example.targetservice.service.OrderService.calculateUnitPrice(OrderService.java:10)";
        };
    }

    private DemoFaultType parseFaultType(String faultType) {
        if (faultType == null || faultType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "faultType is required");
        }
        try {
            return DemoFaultType.fromWireName(faultType.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private String requestedSessionId(DemoScenarioStartRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            return "scenario-" + UUID.randomUUID();
        }
        return validateSessionId(request.getSessionId());
    }

    private String validateSessionId(String sessionId) {
        String trimmed = sessionId == null ? "" : sessionId.trim();
        if (!SESSION_ID_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sessionId must match [A-Za-z0-9._-]{1,80}");
        }
        return trimmed;
    }

    private List<String> waitingNextSteps(String sessionId) {
        List<String> steps = new ArrayList<>();
        steps.add("Restart target-service so the injected source-level fault is loaded.");
        steps.add("POST /api/demo/scenarios/" + sessionId + "/confirm-target-restarted");
        steps.add("Then watch GET /api/repair/stream/" + sessionId);
        return steps;
    }

    private List<String> waitingPullRequestNextSteps(String sessionId, String worktreePath) {
        List<String> steps = new ArrayList<>();
        steps.add("Start or restart target-service from worktree: " + worktreePath);
        steps.add("POST /api/demo/pr-scenarios/" + sessionId + "/confirm-target-restarted");
        steps.add("Then watch GET /api/repair/stream/" + sessionId);
        return steps;
    }

    private DemoScenarioResult copy(
            DemoScenarioResult current,
            DemoScenarioStage stage,
            boolean success,
            String message,
            List<String> nextSteps,
            String repairStreamUrl,
            String evidenceSummary) {
        return new DemoScenarioResult(
                current.sessionId(),
                current.faultType(),
                stage,
                success,
                message,
                current.changedFiles(),
                nextSteps,
                repairStreamUrl,
                current.targetServiceUrl(),
                evidenceSummary,
                current.branchName(),
                current.worktreePath(),
                current.prUrl(),
                current.notificationSuccess(),
                current.recordJsonPath(),
                current.recordMarkdownPath(),
                current.outcomeReason());
    }

    private DemoScenarioResult refreshFromRecord(
            Map<String, DemoScenarioResult> store, String sessionId, DemoScenarioResult current) {
        if (current.stage() != DemoScenarioStage.RUNNING) {
            return current;
        }
        return repairRecordTools.readRecord(sessionId)
                .map(record -> {
                    DemoScenarioResult updated = copyFromRecord(current, record);
                    store.put(sessionId, updated);
                    return updated;
                })
                .orElse(current);
    }

    private DemoScenarioResult copyFromRecord(DemoScenarioResult current, RepairRecord record) {
        String prUrl = record.pullRequestResult() == null ? "" : record.pullRequestResult().url();
        Boolean notificationSuccess = record.notificationResult() == null
                ? null
                : record.notificationResult().success();
        String jsonPath = repairRecordTools.jsonRecordPath(current.sessionId());
        String markdownPath = repairRecordTools.markdownRecordPath(current.sessionId());
        return new DemoScenarioResult(
                current.sessionId(),
                current.faultType(),
                stageFromOutcome(record.outcome()),
                record.outcome() == RepairOutcome.FIXED,
                record.outcomeReason(),
                current.changedFiles(),
                List.of(
                        "Repair record: " + jsonPath,
                        "Markdown report: " + markdownPath,
                        prUrl == null || prUrl.isBlank() ? "PR was not created." : "PR: " + prUrl),
                current.repairStreamUrl(),
                current.targetServiceUrl(),
                current.evidenceSummary(),
                current.branchName(),
                current.worktreePath(),
                prUrl,
                notificationSuccess,
                jsonPath,
                markdownPath,
                record.outcomeReason());
    }

    private DemoScenarioStage stageFromOutcome(RepairOutcome outcome) {
        if (outcome == RepairOutcome.FIXED) {
            return DemoScenarioStage.FIXED;
        }
        if (outcome == RepairOutcome.ERROR) {
            return DemoScenarioStage.ERROR;
        }
        return DemoScenarioStage.FAILED;
    }

    private Path parseWorktreePath(DemoScenarioResult current) {
        if (current.worktreePath() == null || current.worktreePath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PR demo scenario has no repair worktree path: " + current.sessionId());
        }
        return Path.of(current.worktreePath()).toAbsolutePath().normalize();
    }

    private String targetServiceBaseUrl() {
        String baseUrl = properties.getTargetProject().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:9910";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String trim(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "\n... trimmed ...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ScenarioEvidence(boolean success, String message, String summary, List<String> nextSteps) {

        private static ScenarioEvidence success(String summary) {
            return new ScenarioEvidence(true, summary, summary, List.of());
        }

        private static ScenarioEvidence failure(String message, List<String> nextSteps) {
            return new ScenarioEvidence(false, message, "", nextSteps);
        }
    }
}
