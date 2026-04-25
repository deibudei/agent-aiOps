package org.example.agentaiops.repair.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.agent.RepairExecutorAgent;
import org.example.agentaiops.repair.agent.RepairPlannerAgent;
import org.example.agentaiops.repair.agent.RepairReflectionAgent;
import org.example.agentaiops.repair.agent.RepairReviewerAgent;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairRunResponse;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.tool.FeishuTools;
import org.example.agentaiops.repair.tool.GitHubTools;
import org.example.agentaiops.repair.tool.GitTools;
import org.example.agentaiops.repair.tool.ReadLogTools;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.example.agentaiops.repair.tool.RunTestTools;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RepairWorkflowService {

    private final RepairProperties properties;
    private final RepairEventHub eventHub;
    private final ReadLogTools readLogTools;
    private final RunTestTools runTestTools;
    private final RepairPlannerAgent plannerAgent;
    private final RepairExecutorAgent executorAgent;
    private final RepairReviewerAgent reviewerAgent;
    private final RepairReflectionAgent reflectionAgent;
    private final GitTools gitTools;
    private final GitHubTools gitHubTools;
    private final FeishuTools feishuTools;
    private final RepairRecordTools repairRecordTools;
    private final Executor repairTaskExecutor;

    public RepairWorkflowService(
            RepairProperties properties,
            RepairEventHub eventHub,
            ReadLogTools readLogTools,
            RunTestTools runTestTools,
            RepairPlannerAgent plannerAgent,
            RepairExecutorAgent executorAgent,
            RepairReviewerAgent reviewerAgent,
            RepairReflectionAgent reflectionAgent,
            GitTools gitTools,
            GitHubTools gitHubTools,
            FeishuTools feishuTools,
            RepairRecordTools repairRecordTools,
            @Qualifier("repairTaskExecutor") Executor repairTaskExecutor) {
        this.properties = properties;
        this.eventHub = eventHub;
        this.readLogTools = readLogTools;
        this.runTestTools = runTestTools;
        this.plannerAgent = plannerAgent;
        this.executorAgent = executorAgent;
        this.reviewerAgent = reviewerAgent;
        this.reflectionAgent = reflectionAgent;
        this.gitTools = gitTools;
        this.gitHubTools = gitHubTools;
        this.feishuTools = feishuTools;
        this.repairRecordTools = repairRecordTools;
        this.repairTaskExecutor = repairTaskExecutor;
    }

    public RepairRunResponse startAsync(String requestedSessionId) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedSessionId;
        eventHub.publish(sessionId, RepairStage.DETECTING, "Repair workflow accepted");
        repairTaskExecutor.execute(() -> run(sessionId));
        return new RepairRunResponse(sessionId, "started", "/api/repair/stream/" + sessionId);
    }

    private void run(String sessionId) {
        Instant startedAt = Instant.now();
        try {
            eventHub.publish(sessionId, RepairStage.DETECTING, "Reading latest target-service traceback");
            ToolExecutionResult traceback = readLogTools.readLatestTraceback(6000);
            String evidence = traceback.success() ? traceback.output() : traceback.error();

            if (!traceback.success()) {
                eventHub.publish(sessionId, RepairStage.TESTING, "No traceback found; running baseline tests");
                TestExecutionResult baseline = runTestTools.runTargetServiceTests();
                evidence = evidence + System.lineSeparator() + trim(baseline.stdout(), 4000);
            }

            eventHub.publish(sessionId, RepairStage.PLANNING, "Generating repair plan");
            RepairPlan plan = plannerAgent.plan(evidence);
            eventHub.publish(sessionId, RepairStage.PLANNING, "Repair plan generated", Map.of("plan", plan));

            eventHub.publish(sessionId, RepairStage.EXECUTING, "Executing repair plan with tool use");
            RepairExecutionResult execution = executorAgent.execute(
                    plan, properties.getWorkflow().getMaxRepairAttempts());
            eventHub.publish(sessionId, RepairStage.PATCHING, execution.patchResult().message(),
                    Map.of("patch", execution.patchResult()));
            eventHub.publish(sessionId, RepairStage.TESTING, "Target-service tests completed",
                    Map.of("testResult", execution.testResult()));

            eventHub.publish(sessionId, RepairStage.REVIEWING, "Reviewing diff and test result");
            ReviewDecision reviewDecision = reviewerAgent.review(execution);
            eventHub.publish(sessionId, RepairStage.REVIEWING, reviewDecision.reason(),
                    Map.of("review", reviewDecision));

            String diff = gitTools.readTargetDiff();
            GitCommitResult gitCommitResult = new GitCommitResult(false, "", "", "Review did not pass");
            PullRequestResult pullRequestResult = new PullRequestResult(false, "", "Review did not pass");
            NotificationResult notificationResult = new NotificationResult(false, "Review did not pass");

            if (reviewDecision.status() == ReviewStatus.PASS) {
                eventHub.publish(sessionId, RepairStage.COMMITTING, "Creating repair branch and commit");
                gitCommitResult = gitTools.commitAndPush(sessionId);
                eventHub.publish(sessionId, RepairStage.COMMITTING, gitCommitResult.message(),
                        Map.of("git", gitCommitResult));

                eventHub.publish(sessionId, RepairStage.PR_CREATED, "Creating GitHub pull request");
                pullRequestResult = gitHubTools.createPullRequest(
                        gitCommitResult.branchName(),
                        "fix: auto repair target-service validation",
                        buildPrBody(sessionId, plan, reviewDecision));
                eventHub.publish(sessionId, RepairStage.PR_CREATED, pullRequestResult.message(),
                        Map.of("pullRequest", pullRequestResult));
            }

            if (reviewDecision.status() == ReviewStatus.PASS) {
                eventHub.publish(sessionId, RepairStage.NOTIFIED, "Sending Feishu notification");
                notificationResult = feishuTools.sendRepairCard(
                        sessionId,
                        pullRequestResult,
                        plan.rootCauseHypothesis(),
                        reviewDecision.reason());
                eventHub.publish(sessionId, RepairStage.NOTIFIED, notificationResult.message(),
                        Map.of("notification", notificationResult));
            }

            eventHub.publish(sessionId, RepairStage.REFLECTING, "Generating repair reflection");
            RepairReflection reflection = reflectionAgent.reflect(evidence, plan, execution, reviewDecision);
            eventHub.publish(sessionId, RepairStage.REFLECTING, "Repair reflection generated",
                    Map.of("reflection", reflection));

            RepairRecord record = new RepairRecord(
                    1,
                    sessionId,
                    startedAt,
                    Instant.now(),
                    trim(evidence, 3000),
                    plan,
                    execution.stepResults(),
                    trim(diff, 6000),
                    execution.testResult(),
                    reviewDecision,
                    gitCommitResult,
                    pullRequestResult,
                    notificationResult,
                    reflection);
            repairRecordTools.writeRecord(record);
            eventHub.publish(sessionId, RepairStage.COMPLETED, "Repair workflow completed",
                    Map.of("recordVersion", record.recordVersion()));
        } catch (Exception e) {
            eventHub.publish(sessionId, RepairStage.ERROR, "Repair workflow failed: " + e.getMessage());
        }
    }

    private String buildPrBody(String sessionId, RepairPlan plan, ReviewDecision reviewDecision) {
        return """
                ## Auto Repair

                Session: `%s`

                ### Plan
                %s

                ### Review
                %s
                """.formatted(sessionId, plan, reviewDecision.reason());
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n... trimmed ...";
    }
}
