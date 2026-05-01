package org.example.agentaiops.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairWorktreeResult;
import org.example.agentaiops.repair.model.RepairRunResponse;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.service.RepairWorkflowService;
import org.example.agentaiops.repair.tool.GitTools;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.example.agentaiops.repair.tool.RepairWorkspaceContext;
import org.example.agentaiops.repair.tool.RunTestTools;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class DemoScenarioServiceTest {

    @TempDir
    Path tempDir;

    private DemoScenarioService service;
    private RepairWorkflowService repairWorkflowService;
    private RunTestTools runTestTools;
    private HttpClient httpClient;
    private GitTools gitTools;
    private RepairRecordTools repairRecordTools;
    private RepairWorkspaceContext workspaceContext;
    private RepairProperties properties;
    private DemoFaultService demoFaultService;
    private ToolPolicy toolPolicy;

    @BeforeEach
    void setUp() throws Exception {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createDirectories(tempDir.resolve("agent-platform"));
        Files.createFile(tempDir.resolve("agent-platform/pom.xml"));
        Files.createDirectories(tempDir.resolve("target-service/src/main/java/com/example/targetservice/service"));
        Files.createDirectories(tempDir.resolve("target-service/src/main/java/com/example/targetservice/controller"));
        Files.createDirectories(tempDir.resolve("target-service/src/main/java/com/example/targetservice/web"));
        Files.createFile(tempDir.resolve("target-service/pom.xml"));

        properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.getTargetProject().setBaseUrl("http://localhost:9910");
        toolPolicy = new ToolPolicy(properties);
        demoFaultService = new DemoFaultService(toolPolicy);
        demoFaultService.reset();

        repairWorkflowService = mock(RepairWorkflowService.class);
        runTestTools = mock(RunTestTools.class);
        httpClient = mock(HttpClient.class);
        gitTools = mock(GitTools.class);
        repairRecordTools = mock(RepairRecordTools.class);
        when(repairRecordTools.readRecord(any())).thenReturn(Optional.empty());
        workspaceContext = new RepairWorkspaceContext();
        service = new DemoScenarioService(
                demoFaultService,
                repairWorkflowService,
                runTestTools,
                toolPolicy,
                gitTools,
                repairRecordTools,
                workspaceContext,
                properties,
                httpClient);
    }

    @Test
    void startInjectsFaultAndWaitsForManualRestart() {
        DemoScenarioResult result = service.start(request("scenario-001", "quantity-division-by-zero"));

        assertThat(result.stage()).isEqualTo(DemoScenarioStage.WAITING_FOR_TARGET_RESTART);
        assertThat(result.success()).isTrue();
        assertThat(result.changedFiles())
                .contains("target-service/src/main/java/com/example/targetservice/service/OrderService.java");
        assertThat(result.nextSteps()).anySatisfy(step -> assertThat(step).contains("confirm-target-restarted"));
        verify(repairWorkflowService, never()).startAsync(any());
    }

    @Test
    void rejectsDuplicateSessionId() {
        service.start(request("scenario-duplicate", "quantity-division-by-zero"));

        assertThatThrownBy(() -> service.start(request("scenario-duplicate", "wrong-quote-route")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsUnsupportedFaultType() {
        assertThatThrownBy(() -> service.start(request("scenario-bad", "missing-fault")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported demo fault type");
    }

    @Test
    void rejectsSourceInjectionScenarioWhenGitAutomationIsEnabled() {
        properties.getGit().setEnabled(true);
        service = new DemoScenarioService(
                demoFaultService,
                repairWorkflowService,
                runTestTools,
                toolPolicy,
                gitTools,
                repairRecordTools,
                workspaceContext,
                properties,
                httpClient);

        assertThatThrownBy(() -> service.start(request("scenario-git", "quantity-division-by-zero")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("REPAIR_GIT_ENABLED=false");
    }

    @Test
    void startsPullRequestScenarioFromConfiguredFaultBaseBranch() {
        properties.getGit().setEnabled(true);
        properties.getGithub().setEnabled(true);
        properties.getGit().setBaseBranch("demo/fault/quantity-division-by-zero");
        when(gitTools.prepareRepairWorktreeFromBase("scenario-pr"))
                .thenReturn(new RepairWorktreeResult(
                        true,
                        "repair/scenario-pr",
                        tempDir.resolve("../worktrees/scenario-pr").toString(),
                        "prepared"));

        DemoScenarioResult result = service.startPullRequestScenario(
                request("scenario-pr", "quantity-division-by-zero"));

        assertThat(result.stage()).isEqualTo(DemoScenarioStage.WAITING_FOR_TARGET_RESTART);
        assertThat(result.message()).contains("repair/scenario-pr");
        assertThat(result.worktreePath()).contains("scenario-pr");
        assertThat(result.evidenceSummary()).contains("demo/fault/quantity-division-by-zero");
    }

    @Test
    void rejectsPullRequestScenarioWhenBaseBranchDoesNotMatchFaultType() {
        properties.getGit().setEnabled(true);
        properties.getGithub().setEnabled(true);
        properties.getGit().setBaseBranch("demo/fault/quantity-division-by-zero");

        assertThatThrownBy(() -> service.startPullRequestScenario(request("scenario-route-pr", "wrong-quote-route")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("REPAIR_BASE_BRANCH=demo/fault/wrong-quote-route");
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmPullRequestScenarioStartsRepairAfterEvidence() throws Exception {
        properties.getGit().setEnabled(true);
        properties.getGithub().setEnabled(true);
        properties.getGit().setBaseBranch("demo/fault/quantity-division-by-zero");
        Path worktreePath = tempDir.resolve("../worktrees/scenario-pr-confirm").toAbsolutePath().normalize();
        when(gitTools.prepareRepairWorktreeFromBase("scenario-pr-confirm"))
                .thenReturn(new RepairWorktreeResult(
                        true,
                        "repair/scenario-pr-confirm",
                        worktreePath.toString(),
                        "prepared"));
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(repairWorkflowService.startAsync("scenario-pr-confirm", worktreePath.toString()))
                .thenReturn(new RepairRunResponse(
                        "scenario-pr-confirm", "started", "/api/repair/stream/scenario-pr-confirm"));
        service.startPullRequestScenario(request("scenario-pr-confirm", "quantity-division-by-zero"));

        DemoScenarioResult result = service.confirmPullRequestTargetRestarted("scenario-pr-confirm");

        assertThat(result.stage()).isEqualTo(DemoScenarioStage.RUNNING);
        assertThat(result.repairStreamUrl()).isEqualTo("/api/repair/stream/scenario-pr-confirm");
        verify(repairWorkflowService).startAsync("scenario-pr-confirm", worktreePath.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRefreshesRunningScenarioFromRepairRecord() {
        properties.getGit().setEnabled(true);
        properties.getGithub().setEnabled(true);
        properties.getGit().setBaseBranch("demo/fault/quantity-division-by-zero");
        Path worktreePath = tempDir.resolve("../worktrees/scenario-finished").toAbsolutePath().normalize();
        when(gitTools.prepareRepairWorktreeFromBase("scenario-finished"))
                .thenReturn(new RepairWorktreeResult(
                        true,
                        "repair/scenario-finished",
                        worktreePath.toString(),
                        "prepared"));
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        try {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        when(repairWorkflowService.startAsync("scenario-finished", worktreePath.toString()))
                .thenReturn(new RepairRunResponse(
                        "scenario-finished", "started", "/api/repair/stream/scenario-finished"));
        when(repairRecordTools.readRecord("scenario-finished"))
                .thenReturn(Optional.of(record("scenario-finished", RepairOutcome.FIXED)));
        when(repairRecordTools.jsonRecordPath("scenario-finished"))
                .thenReturn("repair-records/scenario-finished.json");
        when(repairRecordTools.markdownRecordPath("scenario-finished"))
                .thenReturn("repair-records/scenario-finished.md");
        service.startPullRequestScenario(request("scenario-finished", "quantity-division-by-zero"));
        service.confirmPullRequestTargetRestarted("scenario-finished");

        DemoScenarioResult result = service.get("scenario-finished");

        assertThat(result.stage()).isEqualTo(DemoScenarioStage.FIXED);
        assertThat(result.success()).isTrue();
        assertThat(result.prUrl()).isEqualTo("https://github.com/deibudei/agent-aiOps/pull/9");
        assertThat(result.recordJsonPath()).isEqualTo("repair-records/scenario-finished.json");
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmTargetRestartedTriggersRuntimeFailureAndStartsRepair() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(repairWorkflowService.startAsync("scenario-runtime"))
                .thenReturn(new RepairRunResponse(
                        "scenario-runtime", "started", "/api/repair/stream/scenario-runtime"));
        service.start(request("scenario-runtime", "quantity-division-by-zero"));

        DemoScenarioResult result = service.confirmTargetRestarted("scenario-runtime");

        assertThat(result.stage()).isEqualTo(DemoScenarioStage.RUNNING);
        assertThat(result.repairStreamUrl()).isEqualTo("/api/repair/stream/scenario-runtime");
        assertThat(result.evidenceSummary()).contains("HTTP 500");
        verify(repairWorkflowService).startAsync("scenario-runtime");
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsWaitingWhenRuntimeFaultWasNotLoadedByRestart() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        service.start(request("scenario-no-restart", "quantity-division-by-zero"));

        DemoScenarioResult result = service.confirmTargetRestarted("scenario-no-restart");

        assertThat(result.stage()).isEqualTo(DemoScenarioStage.WAITING_FOR_TARGET_RESTART);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("may still be running fixed code");
        verify(repairWorkflowService, never()).startAsync(any());
    }

    @Test
    void writesTestFailureEvidenceForNonRuntimeFaults() throws Exception {
        when(runTestTools.runTargetServiceTests()).thenReturn(new TestExecutionResult(
                1,
                "OrderControllerTest expected status 200 but was 404",
                "",
                1234,
                false));
        when(repairWorkflowService.startAsync("scenario-route"))
                .thenReturn(new RepairRunResponse(
                        "scenario-route", "started", "/api/repair/stream/scenario-route"));
        service.start(request("scenario-route", "wrong-quote-route"));

        DemoScenarioResult result = service.confirmTargetRestarted("scenario-route");

        assertThat(result.stage()).isEqualTo(DemoScenarioStage.RUNNING);
        assertThat(result.evidenceSummary()).contains("test-failure evidence");
        Path tracebacks = tempDir.resolve("target-service/logs/tracebacks");
        try (var stream = Files.list(tracebacks)) {
            Path evidence = stream.findFirst().orElseThrow();
            String content = Files.readString(evidence);
            assertThat(content).contains("faultType=wrong-quote-route");
            assertThat(content).contains("OrderControllerTest expected status 200 but was 404");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsConfirmAfterRepairHasStarted() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(repairWorkflowService.startAsync("scenario-started"))
                .thenReturn(new RepairRunResponse(
                        "scenario-started", "started", "/api/repair/stream/scenario-started"));
        service.start(request("scenario-started", "quantity-division-by-zero"));
        service.confirmTargetRestarted("scenario-started");

        assertThatThrownBy(() -> service.confirmTargetRestarted("scenario-started"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not waiting");
    }

    private DemoScenarioStartRequest request(String sessionId, String faultType) {
        DemoScenarioStartRequest request = new DemoScenarioStartRequest();
        request.setSessionId(sessionId);
        request.setFaultType(faultType);
        return request;
    }

    private RepairRecord record(String sessionId, RepairOutcome outcome) {
        Instant now = Instant.parse("2026-04-30T08:00:00Z");
        return new RepairRecord(
                1,
                sessionId,
                now,
                now.plusSeconds(1),
                outcome,
                "Patch passed review and PR was created",
                null,
                "",
                null,
                List.of(),
                null,
                null,
                "",
                null,
                null,
                null,
                new PullRequestResult(true, "https://github.com/deibudei/agent-aiOps/pull/9", "created"),
                new NotificationResult(true, "sent"),
                null,
                null);
    }
}
