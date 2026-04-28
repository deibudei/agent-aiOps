package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agentic.AgenticServices;
import java.time.Instant;
import java.util.Map;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.llm.RepairChatModelProvider;
import org.example.agentaiops.llm.RepairModelRole;
import org.example.agentaiops.repair.agent.EvidenceAgent;
import org.example.agentaiops.repair.agent.RepairReflectionAgent;
import org.example.agentaiops.repair.agent.RepairReviewerAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticDiagnosisAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticPatchAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticPatchRegenerationAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticPlanAgent;
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

/** Runs the deterministic Java repair DAG with three LangChain4j AI sub-agents. */
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
    private final RepairReflectionAgent reflectionAgent;
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
            RepairReflectionAgent reflectionAgent,
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
        this.reflectionAgent = reflectionAgent;
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
                properties.getAgentic().isFileReadCacheEnabled());
        RepairAgenticListener listener = new RepairAgenticListener(
                state,
                eventHub,
                roleByAgent(),
                modelByAgent());

        AgenticDiagnosisAgent diagnosisAgent = AgenticServices.agentBuilder(AgenticDiagnosisAgent.class)
                .chatModel(chatModelProvider.chatModel(RepairModelRole.DIAGNOSIS))
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPlanAgent planAgent = AgenticServices.agentBuilder(AgenticPlanAgent.class)
                .chatModel(chatModelProvider.chatModel(RepairModelRole.PLAN))
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPatchAgent patchAgent = AgenticServices.agentBuilder(AgenticPatchAgent.class)
                .chatModel(chatModelProvider.chatModel(RepairModelRole.PATCH))
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPatchRegenerationAgent patchRegenerationAgent = AgenticServices
                .agentBuilder(AgenticPatchRegenerationAgent.class)
                .chatModel(chatModelProvider.chatModel(RepairModelRole.PATCH))
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

        evidenceOperator.collectEvidence();

        DiagnosisResult diagnosis = diagnosisAgent.diagnoseRootCause(state.evidence);
        state.diagnosis = diagnosis;

        RepairPlan plan = planAgent.generateRepairPlan(state.evidence, diagnosis);
        planParserOperator.parseRepairPlan(plan);

        sourceContextOperator.prepareSourceContext(state.evidence, state.plan);

        runReflexionLoop(patchAgent, patchRegenerationAgent, patchParserOperator,
                patchApplyOperator, testOperator, state);

        reviewOperator.reviewRepair();

        if (state.reviewDecision != null && state.reviewDecision.status() == ReviewStatus.PASS) {
            commitOperator.commitRepair();
            pullRequestOperator.createPullRequest();
        } else {
            eventHub.publish(sessionId, RepairStage.COMMITTING,
                    "Skipping commit/PR because review did not pass");
        }

        notifyOperator.sendNotification();

        reflectOperator.reflectRepair();

        recordOperator.writeRepairRecord();

        var timing = state.timing();
        long durationMillis = timing.durationMillis();
        eventHub.publish(sessionId, RepairStage.COMPLETED, "Repair workflow completed",
                Map.of(
                        "recordVersion", 1,
                        "mode", "java-dag",
                        "stepName", "repairWorkflow",
                        "durationMillis", durationMillis,
                        "patchAttempts", state.patchAttempts,
                        "modelUsage", timing.modelUsage()));
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
                proposal = patchAgent.generatePatchProposal(state.plan, state.sourceContext);
            } else {
                eventHub.publish(state.sessionId, RepairStage.PATCHING,
                        "Reflexion attempt " + attempt + ": regenerating patch from test stderr");
                proposal = patchRegenerationAgent.regeneratePatchFromTestFailure(
                        state.plan,
                        state.sourceContext,
                        previousProposal,
                        AgenticEvidenceFormatter.trim(testStderr(lastTestResult), TEST_STDERR_PROMPT_CHARS));
            }
            patchParserOperator.parsePatchProposal(proposal);
            previousProposal = state.patchProposal;

            patchApplyOperator.applyPatchProposal(state.patchProposal);
            if (!state.patchApplicationResult.success()) {
                return;
            }

            lastTestResult = testOperator.runTargetTests();
            if (lastTestResult.success()) {
                return;
            }
            if (attempt == maxAttempts) {
                return;
            }

            patchApplyOperator.rollbackLastApply();
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

    private Map<String, String> roleByAgent() {
        return Map.of(
                "diagnoseRootCause", RepairModelRole.DIAGNOSIS.name(),
                "generateRepairPlan", RepairModelRole.PLAN.name(),
                "generatePatchProposal", RepairModelRole.PATCH.name(),
                "regeneratePatchProposal", RepairModelRole.PATCH.name());
    }

    private Map<String, String> modelByAgent() {
        return Map.of(
                "diagnoseRootCause", chatModelProvider.modelName(RepairModelRole.DIAGNOSIS),
                "generateRepairPlan", chatModelProvider.modelName(RepairModelRole.PLAN),
                "generatePatchProposal", chatModelProvider.modelName(RepairModelRole.PATCH),
                "regeneratePatchProposal", chatModelProvider.modelName(RepairModelRole.PATCH));
    }
}
