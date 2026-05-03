package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agentic.AgenticServices;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.llm.RepairChatModelProvider;
import org.example.agentaiops.llm.RepairModelRole;
import org.example.agentaiops.repair.agent.EvidenceAgent;
import org.example.agentaiops.repair.agent.RepairReviewerAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticDiagnosisAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticPatchAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticPatchRegenerationAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticPlanAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticReflectionAgent;
import org.example.agentaiops.repair.agentic.operators.CommitOperator;
import org.example.agentaiops.repair.agentic.operators.EvidenceOperator;
import org.example.agentaiops.repair.agentic.operators.NotifyOperator;
import org.example.agentaiops.repair.agentic.operators.PatchApplyOperator;
import org.example.agentaiops.repair.agentic.operators.PatchParserOperator;
import org.example.agentaiops.repair.agentic.operators.PlanParserOperator;
import org.example.agentaiops.repair.agentic.operators.PullRequestOperator;
import org.example.agentaiops.repair.agentic.operators.RecordOperator;
import org.example.agentaiops.repair.agentic.operators.ReflectOperator;
import org.example.agentaiops.repair.agentic.operators.ReviewOperator;
import org.example.agentaiops.repair.agentic.operators.SourceContextOperator;
import org.example.agentaiops.repair.agentic.operators.TestOperator;
import org.example.agentaiops.repair.model.DiagnosisResult;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairOutcome;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.FeishuTools;
import org.example.agentaiops.repair.tool.GitHubTools;
import org.example.agentaiops.repair.tool.GitTools;
import org.example.agentaiops.repair.tool.PatchTools;
import org.example.agentaiops.repair.tool.ReadCodeTools;
import org.example.agentaiops.repair.tool.ReadLogTools;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.example.agentaiops.repair.tool.RunTestTools;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.springframework.stereotype.Component;

/** Runs the deterministic Java repair DAG with LangChain4j AI sub-agents. */
@Component
public class AgenticRepairRunner {

    private static final int TEST_STDERR_PROMPT_CHARS = 2000;

    private final RepairProperties properties;
    private final RepairChatModelProvider chatModelProvider;
    private final EvidenceAgent evidenceAgent;
    private final ReadLogTools readLogTools;
    private final ReadCodeTools readCodeTools;
    private final PatchTools patchTools;
    private final ToolPolicy toolPolicy;
    private final RunTestTools runTestTools;
    private final RepairReviewerAgent reviewerAgent;
    private final GitTools gitTools;
    private final GitHubTools gitHubTools;
    private final FeishuTools feishuTools;
    private final RepairRecordTools repairRecordTools;
    private final RepairEventHub eventHub;

    /** Wires deterministic operators and the LangChain4j agentic AI sub-agents. */
    public AgenticRepairRunner(
            RepairProperties properties,
            RepairChatModelProvider chatModelProvider,
            EvidenceAgent evidenceAgent,
            ReadLogTools readLogTools,
            ReadCodeTools readCodeTools,
            PatchTools patchTools,
            ToolPolicy toolPolicy,
            RunTestTools runTestTools,
            RepairReviewerAgent reviewerAgent,
            GitTools gitTools,
            GitHubTools gitHubTools,
            FeishuTools feishuTools,
            RepairRecordTools repairRecordTools,
            RepairEventHub eventHub) {
        this.properties = properties;
        this.chatModelProvider = chatModelProvider;
        this.evidenceAgent = evidenceAgent;
        this.readLogTools = readLogTools;
        this.readCodeTools = readCodeTools;
        this.patchTools = patchTools;
        this.toolPolicy = toolPolicy;
        this.runTestTools = runTestTools;
        this.reviewerAgent = reviewerAgent;
        this.gitTools = gitTools;
        this.gitHubTools = gitHubTools;
        this.feishuTools = feishuTools;
        this.repairRecordTools = repairRecordTools;
        this.eventHub = eventHub;
    }

    /** Returns true when the workflow has a configured LLM. */
    public boolean available() {
        return chatModelProvider.available();
    }

    /** Runs one deterministic repair session. */
    public void run(String sessionId, Instant startedAt) {
        if (!available()) {
            throw new IllegalStateException("LangChain4j repair requires a configured LLM");
        }

        AgenticRepairState state = new AgenticRepairState(sessionId, startedAt);
        AgenticReadOnlyTools readOnlyTools = new AgenticReadOnlyTools(
                readLogTools,
                readCodeTools,
                properties.getAgentic().isFileReadCacheEnabled(),
                sessionId,
                eventHub);
        RepairAgenticListener listener = new RepairAgenticListener(
                state,
                eventHub,
                roleByAgent(),
                modelByAgent());

        AgenticDiagnosisAgent diagnosisAgent = AgenticServices.agentBuilder(AgenticDiagnosisAgent.class)
                .chatModel(observedChatModel(state, "diagnoseRootCause", RepairModelRole.DIAGNOSIS))
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPlanAgent planAgent = AgenticServices.agentBuilder(AgenticPlanAgent.class)
                .chatModel(observedChatModel(state, "generateRepairPlan", RepairModelRole.PLAN))
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPatchAgent patchAgent = AgenticServices.agentBuilder(AgenticPatchAgent.class)
                .chatModel(observedChatModel(state, "generatePatchProposal", RepairModelRole.PATCH))
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPatchRegenerationAgent patchRegenerationAgent = AgenticServices
                .agentBuilder(AgenticPatchRegenerationAgent.class)
                .chatModel(observedChatModel(state, "regeneratePatchProposal", RepairModelRole.PATCH))
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticReflectionAgent reflectionAgent = AgenticServices.agentBuilder(AgenticReflectionAgent.class)
                .chatModel(observedChatModel(state, "reflectRepair", RepairModelRole.REFLECTION))
                .tools(readOnlyTools)
                .listener(listener)
                .build();

        EvidenceOperator evidenceOperator = new EvidenceOperator(state, evidenceAgent, eventHub);
        PlanParserOperator planParserOperator = new PlanParserOperator(state, eventHub);
        SourceContextOperator sourceContextOperator = new SourceContextOperator(state);
        PatchParserOperator patchParserOperator = new PatchParserOperator(state, eventHub);
        PatchApplyOperator patchApplyOperator = new PatchApplyOperator(state, patchTools, toolPolicy, eventHub);
        TestOperator testOperator = new TestOperator(state, runTestTools, properties, eventHub);
        ReviewOperator reviewOperator = new ReviewOperator(state, reviewerAgent, eventHub);
        CommitOperator commitOperator = new CommitOperator(state, gitTools, eventHub);
        PullRequestOperator pullRequestOperator = new PullRequestOperator(state, gitHubTools, eventHub);
        NotifyOperator notifyOperator = new NotifyOperator(state, feishuTools, eventHub);
        ReflectOperator reflectOperator = new ReflectOperator(state, reflectionAgent, eventHub);
        RecordOperator recordOperator = new RecordOperator(state, repairRecordTools, gitTools, eventHub);

        eventHub.publish(sessionId, RepairStage.EXECUTING, "Starting deterministic repair DAG");

        timed(state, "collectEvidence", evidenceOperator::collectEvidence, "Evidence collected");

        DiagnosisResult diagnosis = diagnoseWithOneRetry(diagnosisAgent, state);
        state.diagnosis = diagnosis;

        planWithOneRetry(planAgent, planParserOperator, state, diagnosis);

        timed(state, "prepareSourceContext",
                () -> sourceContextOperator.prepareSourceContext(state.evidence, state.plan),
                "Source context prepared");

        runReflexionLoop(patchAgent, patchRegenerationAgent, patchParserOperator,
                patchApplyOperator, testOperator, state);

        timed(state, "reviewRepair", reviewOperator::reviewRepair, "Repair reviewed");
        captureReviewDiff(state);

        if (state.reviewDecision != null && state.reviewDecision.status() == ReviewStatus.PASS) {
            timed(state, "commitRepair", commitOperator::commitRepair, "Commit step completed");
            if (gitFailureShouldFailRun(state)) {
                state.markOutcome(RepairOutcome.FAILED, state.gitCommitResult.message());
            } else {
                timed(state, "createPullRequest", pullRequestOperator::createPullRequest,
                        "Pull request step completed");
                state.markOutcome(outcomeAfterPr(state), outcomeReasonAfterPr(state));
            }
        } else {
            state.markOutcome(RepairOutcome.FAILED,
                    state.reviewDecision == null ? "Review did not produce a decision" : state.reviewDecision.reason());
            eventHub.publish(sessionId, RepairStage.COMMITTING,
                    "Skipping commit/PR because review did not pass");
        }

        timed(state, "sendNotification", notifyOperator::sendNotification, "Notification step completed");

        timed(state, "reflectRepair", reflectOperator::reflectRepair, "Reflection completed");

        recordOperator.writeRepairRecord();

        var timing = state.timing();
        long durationMillis = timing.durationMillis();
        eventHub.publish(sessionId, RepairStage.COMPLETED, "Repair workflow completed",
                Map.of(
                        "recordVersion", 1,
                        "mode", "java-dag",
                        "stepName", "repairWorkflow",
                        "durationMillis", durationMillis,
                        "outcome", state.outcome,
                        "outcomeReason", state.outcomeReason,
                        "patchAttempts", state.patchAttempts,
                        "modelUsage", timing.modelUsage()));
    }

    private void captureReviewDiff(AgenticRepairState state) {
        if (state.diff == null) {
            state.diff = gitTools.readTargetDiff();
        }
        if (state.diffFiles == null) {
            state.diffFiles = GitTools.parseDiffFiles(state.diff);
        }
    }

    /** Generates -> validates -> applies -> tests, with reflexion retry on failure. */
    private void runReflexionLoop(
            AgenticPatchAgent patchAgent,
            AgenticPatchRegenerationAgent patchRegenerationAgent,
            PatchParserOperator patchParserOperator,
            PatchApplyOperator patchApplyOperator,
            TestOperator testOperator,
            AgenticRepairState state) {
        int maxAttempts = Math.max(1, properties.getWorkflow().getMaxPatchAttempts());
        PatchProposal previousProposal = null;
        TestExecutionResult lastTestResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            state.patchAttempts = attempt;
            PatchProposal proposal;
            if (attempt == 1) {
                proposal = patchWithOneRetry(
                        "generatePatchProposal",
                        () -> patchAgent.generatePatchProposal(state.plan, state.sourceContext),
                        patchParserOperator,
                        state);
            } else {
                eventHub.publish(state.sessionId, RepairStage.PATCHING,
                        "Reflexion attempt " + attempt + ": regenerating patch from test stderr");
                PatchProposal failedProposal = previousProposal;
                String trimmedTestFailure =
                        AgenticEvidenceFormatter.trim(testStderr(lastTestResult), TEST_STDERR_PROMPT_CHARS);
                proposal = patchWithOneRetry(
                        "regeneratePatchProposal",
                        () -> patchRegenerationAgent.regeneratePatchFromTestFailure(
                                state.plan,
                                state.sourceContext,
                                failedProposal,
                                trimmedTestFailure),
                        patchParserOperator,
                        state);
            }
            previousProposal = state.patchProposal;

            timed(state, "applyPatchProposal",
                    () -> patchApplyOperator.applyPatchProposal(state.patchProposal),
                    "Patch apply completed");
            if (!state.patchApplicationResult.success()) {
                return;
            }

            lastTestResult = timed(state, "runTargetTests",
                    testOperator::runTargetTests,
                    "Target-service tests completed");
            if (lastTestResult.success()) {
                return;
            }
            if (attempt == maxAttempts) {
                return;
            }

            timed(state, "rollbackPatchProposal",
                    patchApplyOperator::rollbackLastApply,
                    "Rolled back failed patch attempt");
        }
    }

    private String testStderr(TestExecutionResult testResult) {
        if (testResult == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (testResult.stdout() != null && !testResult.stdout().isBlank()) {
            sb.append(testResult.stdout());
        }
        if (testResult.stderr() != null && !testResult.stderr().isBlank()) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(testResult.stderr());
        }
        return sb.toString();
    }

    private DiagnosisResult diagnoseWithOneRetry(AgenticDiagnosisAgent diagnosisAgent, AgenticRepairState state) {
        RuntimeException firstFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                DiagnosisResult diagnosis = timed(state, "diagnoseRootCause", () -> {
                    DiagnosisResult result = diagnosisAgent.diagnoseRootCause(state.evidence);
                    validateDiagnosis(result);
                    return result;
                }, "Diagnosis output validated");
                return diagnosis;
            } catch (RuntimeException e) {
                firstFailure = e;
                eventHub.publish(state.sessionId, RepairStage.PLANNING,
                        "Diagnosis output invalid on attempt " + attempt + ": " + e.getMessage());
            }
        }
        throw firstFailure;
    }

    private void planWithOneRetry(
            AgenticPlanAgent planAgent,
            PlanParserOperator planParserOperator,
            AgenticRepairState state,
            DiagnosisResult diagnosis) {
        RuntimeException firstFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                timed(state, "generateRepairPlan", () -> {
                    RepairPlan plan = planAgent.generateRepairPlan(state.evidence, diagnosis);
                    planParserOperator.parseRepairPlan(plan);
                    return plan;
                }, "Repair plan output validated");
                return;
            } catch (RuntimeException e) {
                firstFailure = e;
                eventHub.publish(state.sessionId, RepairStage.PLANNING,
                        "RepairPlan output invalid on attempt " + attempt + ": " + e.getMessage());
            }
        }
        throw firstFailure;
    }

    private PatchProposal patchWithOneRetry(
            String stepName,
            Supplier<PatchProposal> proposalSupplier,
            PatchParserOperator patchParserOperator,
            AgenticRepairState state) {
        RuntimeException firstFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return timed(state, stepName,
                        () -> patchParserOperator.parsePatchProposal(proposalSupplier.get()),
                        "Patch proposal output validated");
            } catch (RuntimeException e) {
                firstFailure = e;
                eventHub.publish(state.sessionId, RepairStage.PATCHING,
                        "PatchProposal output invalid on attempt " + attempt + ": " + e.getMessage());
            }
        }
        throw firstFailure;
    }

    private void validateDiagnosis(DiagnosisResult diagnosis) {
        if (diagnosis == null) {
            throw new IllegalStateException("Agentic diagnosis output was not a DiagnosisResult");
        }
        requireDiagnosisText(diagnosis.failureSignal(), "failureSignal");
        requireDiagnosisText(diagnosis.primaryEvidence(), "primaryEvidence");
        requireDiagnosisText(diagnosis.rootCause(), "rootCause");
        requireDiagnosisText(diagnosis.violatedContract(), "violatedContract");
        if (diagnosis.suspectedFiles() == null || diagnosis.suspectedFiles().isEmpty()) {
            throw new IllegalStateException("DiagnosisResult suspectedFiles must not be empty");
        }
        if (diagnosis.confidence() < 0.0 || diagnosis.confidence() > 1.0) {
            throw new IllegalStateException("DiagnosisResult confidence must be between 0.0 and 1.0");
        }
        for (String file : diagnosis.suspectedFiles()) {
            requireDiagnosisText(file, "suspectedFiles");
            if (!file.startsWith("target-service/src/main/") && !file.startsWith("target-service/src/test/")) {
                throw new IllegalStateException("DiagnosisResult suspected file is outside target-service src: " + file);
            }
        }
    }

    private void requireDiagnosisText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("DiagnosisResult " + fieldName + " is required");
        }
    }

    private boolean gitFailureShouldFailRun(AgenticRepairState state) {
        return properties.getGit().isEnabled()
                && state.gitCommitResult != null
                && !state.gitCommitResult.success();
    }

    private RepairOutcome outcomeAfterPr(AgenticRepairState state) {
        if (properties.getGithub().isEnabled()) {
            PullRequestResult result = state.pullRequestResult;
            return result != null && result.success() && result.url() != null && !result.url().isBlank()
                    ? RepairOutcome.FIXED
                    : RepairOutcome.FAILED;
        }
        return RepairOutcome.FIXED;
    }

    private String outcomeReasonAfterPr(AgenticRepairState state) {
        if (properties.getGithub().isEnabled()) {
            PullRequestResult result = state.pullRequestResult;
            if (result != null && result.success() && result.url() != null && !result.url().isBlank()) {
                return "Patch passed review and PR was created: " + result.url();
            }
            return result == null ? "GitHub PR did not run" : result.message();
        }
        return "Patch passed review and GitHub PR creation is disabled";
    }

    private <T> T timed(AgenticRepairState state, String stepName, Supplier<T> action, String successSummary) {
        state.beginTiming(stepName);
        try {
            T result = action.get();
            state.endTiming(stepName, true, successSummary);
            return result;
        } catch (RuntimeException e) {
            state.endTiming(stepName, false, describe(e));
            throw e;
        }
    }

    private void timed(AgenticRepairState state, String stepName, Runnable action, String successSummary) {
        timed(state, stepName, () -> {
            action.run();
            return null;
        }, successSummary);
    }

    private String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }

    private ObservedChatModel observedChatModel(
            AgenticRepairState state,
            String stepName,
            RepairModelRole role) {
        return new ObservedChatModel(
                chatModelProvider.chatModel(role),
                state,
                stepName,
                role.name(),
                chatModelProvider.modelName(role));
    }

    private Map<String, String> roleByAgent() {
        return Map.of(
                "diagnoseRootCause", RepairModelRole.DIAGNOSIS.name(),
                "generateRepairPlan", RepairModelRole.PLAN.name(),
                "generatePatchProposal", RepairModelRole.PATCH.name(),
                "regeneratePatchProposal", RepairModelRole.PATCH.name(),
                "reflectRepair", RepairModelRole.REFLECTION.name());
    }

    private Map<String, String> modelByAgent() {
        return Map.of(
                "diagnoseRootCause", chatModelProvider.modelName(RepairModelRole.DIAGNOSIS),
                "generateRepairPlan", chatModelProvider.modelName(RepairModelRole.PLAN),
                "generatePatchProposal", chatModelProvider.modelName(RepairModelRole.PATCH),
                "regeneratePatchProposal", chatModelProvider.modelName(RepairModelRole.PATCH),
                "reflectRepair", chatModelProvider.modelName(RepairModelRole.REFLECTION));
    }
}
