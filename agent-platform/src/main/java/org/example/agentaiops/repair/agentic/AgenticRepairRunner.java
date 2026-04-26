package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.llm.RepairChatModelProvider;
import org.example.agentaiops.llm.StructuredJsonParser;
import org.example.agentaiops.repair.agent.EvidenceAgent;
import org.example.agentaiops.repair.agent.RepairReflectionAgent;
import org.example.agentaiops.repair.agent.RepairReviewerAgent;
import org.example.agentaiops.repair.model.EvidenceBundle;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchOperation;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairStage;
import org.example.agentaiops.repair.model.RepairStepResult;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.model.SourceSnippet;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.service.RepairEventHub;
import org.example.agentaiops.repair.tool.FeishuTools;
import org.example.agentaiops.repair.tool.GitHubTools;
import org.example.agentaiops.repair.tool.GitTools;
import org.example.agentaiops.repair.tool.PatchTools;
import org.example.agentaiops.repair.tool.ReadCodeTools;
import org.example.agentaiops.repair.tool.ReadLogTools;
import org.example.agentaiops.repair.tool.RepairRecordTools;
import org.example.agentaiops.repair.tool.RunTestTools;
import org.springframework.stereotype.Component;

@Component
public class AgenticRepairRunner {

    private static final String ORDER_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/OrderService.java";

    private static final String BUGGY_METHOD = ""
            + "    public int calculateUnitPrice(int totalCents, int quantity) {\n"
            + "        return totalCents / quantity;\n"
            + "    }";

    private static final String FIXED_METHOD = ""
            + "    /** Calculates unit price and rejects invalid quantities. */\n"
            + "    public int calculateUnitPrice(int totalCents, int quantity) {\n"
            + "        if (quantity <= 0) {\n"
            + "            throw new IllegalArgumentException(\"quantity must be greater than 0\");\n"
            + "        }\n"
            + "        return totalCents / quantity;\n"
            + "    }";

    private static final int AGENTIC_TRACEBACK_CHARS = 2500;
    private static final int AGENTIC_FILE_CHARS = 2500;
    private static final int AGENTIC_SEARCH_CHARS = 2000;
    private static final int AGENTIC_SOURCE_FILES = 4;
    private static final int AGENTIC_SOURCE_CHARS_PER_FILE = 1600;

    private final RepairProperties properties;
    private final RepairChatModelProvider chatModelProvider;
    private final StructuredJsonParser jsonParser;
    private final EvidenceAgent evidenceAgent;
    private final ReadLogTools readLogTools;
    private final ReadCodeTools readCodeTools;
    private final PatchTools patchTools;
    private final RunTestTools runTestTools;
    private final RepairReviewerAgent reviewerAgent;
    private final RepairReflectionAgent reflectionAgent;
    private final GitTools gitTools;
    private final GitHubTools gitHubTools;
    private final FeishuTools feishuTools;
    private final RepairRecordTools repairRecordTools;
    private final RepairEventHub eventHub;

    /** Wires LangChain4j agentic orchestration around the existing safe tools. */
    public AgenticRepairRunner(
            RepairProperties properties,
            RepairChatModelProvider chatModelProvider,
            StructuredJsonParser jsonParser,
            EvidenceAgent evidenceAgent,
            ReadLogTools readLogTools,
            ReadCodeTools readCodeTools,
            PatchTools patchTools,
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
        this.jsonParser = jsonParser;
        this.evidenceAgent = evidenceAgent;
        this.readLogTools = readLogTools;
        this.readCodeTools = readCodeTools;
        this.patchTools = patchTools;
        this.runTestTools = runTestTools;
        this.reviewerAgent = reviewerAgent;
        this.reflectionAgent = reflectionAgent;
        this.gitTools = gitTools;
        this.gitHubTools = gitHubTools;
        this.feishuTools = feishuTools;
        this.repairRecordTools = repairRecordTools;
        this.eventHub = eventHub;
    }

    /** Returns true when the supervisor path has both a flag and a configured model. */
    public boolean available() {
        return properties.getAgentic().isEnabled() && chatModelProvider.available();
    }

    /** Runs the supervisor-controlled repair workflow for one session. */
    public void run(String sessionId, Instant startedAt) {
        if (!available()) {
            throw new IllegalStateException("LangChain4j agentic repair is disabled or LLM is unavailable");
        }

        SessionState state = new SessionState(sessionId, startedAt);
        AgenticReadOnlyTools readOnlyTools = new AgenticReadOnlyTools(readLogTools, readCodeTools);
        RepairAgenticListener listener = new RepairAgenticListener(sessionId, eventHub);

        AgenticDiagnosisAgent diagnosisAgent = AgenticServices.agentBuilder(AgenticDiagnosisAgent.class)
                .chatModel(chatModelProvider.chatModel())
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPlanAgent planAgent = AgenticServices.agentBuilder(AgenticPlanAgent.class)
                .chatModel(chatModelProvider.chatModel())
                .tools(readOnlyTools)
                .listener(listener)
                .build();
        AgenticPatchAgent patchAgent = AgenticServices.agentBuilder(AgenticPatchAgent.class)
                .chatModel(chatModelProvider.chatModel())
                .tools(readOnlyTools)
                .listener(listener)
                .build();

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(chatModelProvider.chatModel())
                .name("repairSupervisor")
                .description("Autonomously coordinates target-service repair agents")
                .supervisorContext(supervisorContext())
                .subAgents(
                        new EvidenceOperator(state, evidenceAgent, eventHub),
                        diagnosisAgent,
                        planAgent,
                        new PlanParserOperator(state, jsonParser, properties, eventHub),
                        new SourceContextOperator(state),
                        patchAgent,
                        new PatchParserOperator(state, jsonParser, eventHub),
                        new PatchApplyOperator(state, patchTools, eventHub),
                        new TestOperator(state, runTestTools, properties, eventHub),
                        new ReviewOperator(state, reviewerAgent, eventHub),
                        new CommitOperator(state, gitTools, eventHub),
                        new PullRequestOperator(state, gitHubTools, eventHub),
                        new NotifyOperator(state, feishuTools, eventHub),
                        new ReflectOperator(state, reflectionAgent, eventHub),
                        new RecordOperator(state, repairRecordTools, gitTools, eventHub))
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .maxAgentsInvocations(properties.getAgentic().getMaxSupervisorInvocations())
                .listener(listener)
                .build();

        eventHub.publish(sessionId, RepairStage.EXECUTING, "Starting LangChain4j agentic supervisor");
        ResultWithAgenticScope<String> result = supervisor.invokeWithAgenticScope(supervisorRequest());
        state.supervisorSummary = result.result();
        if (!state.recordWritten) {
            new RecordOperator(state, repairRecordTools, gitTools, eventHub).writeRepairRecord();
        }
        eventHub.publish(sessionId, RepairStage.COMPLETED, "Repair workflow completed",
                Map.of("recordVersion", 1, "mode", "langchain4j-agentic"));
    }

    private String supervisorContext() {
        return """
                You supervise a safe Java service repair workflow.
                You must use the available sub-agents to complete the workflow end to end.

                Required order:
                1. collectEvidence
                2. diagnoseRootCause
                3. generateRepairPlan
                4. parseRepairPlan
                5. prepareSourceContext
                6. generatePatchProposal
                7. parsePatchProposal
                8. applyPatchProposal
                9. runTargetTests
                10. reviewRepair
                11. commitRepair
                12. createPullRequest
                13. sendNotification
                14. reflectRepair
                15. writeRepairRecord

                Safety:
                - Only read target-service source and logs.
                - Only patch target-service/src/main or target-service/src/test through PatchTools.
                - GitHub and Feishu agents are allowed in the workflow, but they obey disabled-mode config.
                - Never claim completion until writeRepairRecord has run.
                """;
    }

    private String supervisorRequest() {
        return """
                Repair the current target-service failure.
                Follow the required order from the supervisor context.
                The final result must be a repair record written to repair-records and a concise summary.
                """;
    }

    private static String compactEvidence(EvidenceBundle evidenceBundle) {
        if (evidenceBundle == null) {
            return "";
        }
        String tracebackText = evidenceBundle.traceback() == null
                ? ""
                : evidenceBundle.traceback().success()
                        ? evidenceBundle.traceback().output()
                        : evidenceBundle.traceback().error();
        String testText = evidenceBundle.baselineTestResult() == null
                ? ""
                : evidenceBundle.baselineTestResult().stdout();
        String candidateFiles = evidenceBundle.candidateFiles() == null
                ? ""
                : String.join(System.lineSeparator(), evidenceBundle.candidateFiles());
        return """
                Summary:
                %s

                Traceback:
                %s

                Baseline test output:
                %s

                Candidate files:
                %s
                """.formatted(
                evidenceBundle.summary(),
                trim(tracebackText, AGENTIC_TRACEBACK_CHARS),
                trim(testText, 1200),
                candidateFiles);
    }

    private static String sourceContext(EvidenceBundle evidenceBundle) {
        if (evidenceBundle == null || evidenceBundle.sourceSnippets() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (SourceSnippet snippet : evidenceBundle.sourceSnippets().stream().limit(AGENTIC_SOURCE_FILES).toList()) {
            builder.append("FILE: ").append(snippet.path()).append(System.lineSeparator());
            builder.append("ROLE: ").append(snippet.role()).append(System.lineSeparator());
            builder.append(trim(snippet.content(), AGENTIC_SOURCE_CHARS_PER_FILE))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n... trimmed ...";
    }

    private static PatchResult toPatchResult(PatchApplicationResult applicationResult) {
        String files = applicationResult.patchResults().stream()
                .map(PatchResult::filePath)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new PatchResult(applicationResult.success(), files, applicationResult.message());
    }

    private static String buildPrBody(String sessionId, RepairPlan plan, ReviewDecision reviewDecision) {
        return """
                ## Auto Repair

                Session: `%s`

                ### Plan
                %s

                ### Review
                %s
                """.formatted(sessionId, plan, reviewDecision.reason());
    }

    private static RepairPlan fallbackPlan(RepairProperties properties, String evidence) {
        String hypothesis = "The order quote endpoint lacks validation for quantity <= 0, "
                + "which allows division by zero instead of returning a controlled 400 response.";
        if (evidence != null && evidence.contains("ArithmeticException")) {
            hypothesis = "Traceback shows ArithmeticException: / by zero in OrderService, "
                    + "caused by missing quantity validation.";
        }
        return new RepairPlan(
                "Parameter validation bug in target-service order quote API",
                hypothesis,
                List.of(ORDER_SERVICE),
                List.of(
                        "Read traceback and failing test evidence.",
                        "Read OrderService and locate unit price calculation.",
                        "Add explicit quantity validation before division.",
                        "Run target-service tests.",
                        "Review diff before commit, PR, and notification."),
                properties.getTargetProject().getTestCommand());
    }

    private static PatchProposal fallbackPatchProposal(String rawPatchJson) {
        return new PatchProposal(
                "Parameter validation bug in target-service order quote API",
                "OrderService divides by quantity without checking quantity <= 0.",
                List.of(new PatchOperation(
                        ORDER_SERVICE,
                        BUGGY_METHOD,
                        FIXED_METHOD,
                        "Reject invalid quantities before integer division.")),
                List.of("mvn -pl target-service test"),
                true,
                rawPatchJson == null ? "" : rawPatchJson);
    }

    private static final class SessionState {
        private final String sessionId;
        private final Instant startedAt;
        private final List<RepairStepResult> steps = new ArrayList<>();
        private EvidenceBundle evidenceBundle;
        private String evidence;
        private String diagnosis;
        private String planJson;
        private RepairPlan plan;
        private String sourceContext;
        private String patchJson;
        private PatchProposal patchProposal;
        private PatchApplicationResult patchApplicationResult;
        private PatchResult patchResult;
        private TestExecutionResult testResult;
        private RepairExecutionResult execution;
        private ReviewDecision reviewDecision;
        private GitCommitResult gitCommitResult;
        private PullRequestResult pullRequestResult;
        private NotificationResult notificationResult;
        private RepairReflection reflection;
        private String diff;
        private String supervisorSummary;
        private boolean recordWritten;

        private SessionState(String sessionId, Instant startedAt) {
            this.sessionId = sessionId;
            this.startedAt = startedAt;
            this.gitCommitResult = new GitCommitResult(false, "", "", "Agent did not run commit step");
            this.pullRequestResult = new PullRequestResult(false, "", "Agent did not run PR step");
            this.notificationResult = new NotificationResult(false, "Agent did not run notification step");
        }

        private RepairExecutionResult execution() {
            if (execution == null) {
                execution = new RepairExecutionResult(
                        List.copyOf(steps),
                        testResult,
                        patchResult,
                        patchProposal,
                        patchApplicationResult);
            }
            return execution;
        }

        private void step(String toolName, String input, String output, boolean success) {
            steps.add(new RepairStepResult(toolName, input, output, success));
            execution = null;
        }
    }

    public static final class AgenticReadOnlyTools {
        private final ReadLogTools readLogTools;
        private final ReadCodeTools readCodeTools;

        private AgenticReadOnlyTools(ReadLogTools readLogTools, ReadCodeTools readCodeTools) {
            this.readLogTools = readLogTools;
            this.readCodeTools = readCodeTools;
        }

        @Tool("Read the latest target-service traceback from logs.")
        public String readLatestTraceback(@P("Maximum characters to return") int maxChars) {
            ToolExecutionResult result = readLogTools.readLatestTraceback(Math.min(maxChars, AGENTIC_TRACEBACK_CHARS));
            return result.success() ? trim(result.output(), AGENTIC_TRACEBACK_CHARS) : "ERROR: " + result.error();
        }

        @Tool("Read one target-service source or log file from the whitelist.")
        public String readFile(@P("Repository-relative target-service path") String path) {
            ToolExecutionResult result = readCodeTools.readFile(path);
            return result.success() ? trim(result.output(), AGENTIC_FILE_CHARS) : "ERROR: " + result.error();
        }

        @Tool("Search target-service Java source files for a literal string.")
        public String searchCode(@P("Literal query string") String query) {
            ToolExecutionResult result = readCodeTools.searchCode(query);
            return result.success() ? trim(result.output(), AGENTIC_SEARCH_CHARS) : "ERROR: " + result.error();
        }
    }

    public static final class EvidenceOperator {
        private final SessionState state;
        private final EvidenceAgent evidenceAgent;
        private final RepairEventHub eventHub;

        private EvidenceOperator(SessionState state, EvidenceAgent evidenceAgent, RepairEventHub eventHub) {
            this.state = state;
            this.evidenceAgent = evidenceAgent;
            this.eventHub = eventHub;
        }

        @Agent(name = "collectEvidence", description = "Collect traceback, tests, and source snippets",
                outputKey = "evidence")
        public String collectEvidence() {
            eventHub.publish(state.sessionId, RepairStage.DETECTING,
                    "Agentic EvidenceAgent collecting traceback, tests, and source evidence");
            state.evidenceBundle = evidenceAgent.collect();
            state.evidence = compactEvidence(state.evidenceBundle);
            state.step("EvidenceAgent", "collect", state.evidenceBundle.summary(), true);
            eventHub.publish(state.sessionId, RepairStage.DETECTING, state.evidenceBundle.summary(),
                    Map.of("evidence", state.evidenceBundle));
            return state.evidence;
        }
    }

    public static final class PlanParserOperator {
        private final SessionState state;
        private final StructuredJsonParser jsonParser;
        private final RepairProperties properties;
        private final RepairEventHub eventHub;

        private PlanParserOperator(
                SessionState state,
                StructuredJsonParser jsonParser,
                RepairProperties properties,
                RepairEventHub eventHub) {
            this.state = state;
            this.jsonParser = jsonParser;
            this.properties = properties;
            this.eventHub = eventHub;
        }

        @Agent(name = "parseRepairPlan", description = "Parse strict JSON into RepairPlan", outputKey = "plan")
        public RepairPlan parseRepairPlan(@V("planJson") String planJson) {
            state.planJson = planJson;
            state.plan = jsonParser.parse(planJson, RepairPlan.class)
                    .orElseGet(() -> fallbackPlan(properties, state.evidence));
            boolean parsed = jsonParser.parse(planJson, RepairPlan.class).isPresent();
            state.step("PlanParser", "planJson", parsed ? "RepairPlan parsed" : "Fallback plan used", true);
            eventHub.publish(state.sessionId, RepairStage.PLANNING, "Repair plan generated",
                    Map.of("plan", state.plan));
            return state.plan;
        }
    }

    public static final class SourceContextOperator {
        private final SessionState state;

        private SourceContextOperator(SessionState state) {
            this.state = state;
        }

        @Agent(name = "prepareSourceContext", description = "Prepare selected source snippets for patch generation",
                outputKey = "sourceContext")
        public String prepareSourceContext(@V("evidence") String evidence) {
            state.sourceContext = sourceContext(state.evidenceBundle);
            state.step("SourceContext", "sourceSnippets", "chars=" + state.sourceContext.length(), true);
            return state.sourceContext;
        }
    }

    public static final class PatchParserOperator {
        private final SessionState state;
        private final StructuredJsonParser jsonParser;
        private final RepairEventHub eventHub;

        private PatchParserOperator(SessionState state, StructuredJsonParser jsonParser, RepairEventHub eventHub) {
            this.state = state;
            this.jsonParser = jsonParser;
            this.eventHub = eventHub;
        }

        @Agent(name = "parsePatchProposal", description = "Parse strict JSON into PatchProposal",
                outputKey = "patchProposal")
        public PatchProposal parsePatchProposal(@V("patchJson") String patchJson) {
            state.patchJson = patchJson;
            state.patchProposal = jsonParser.parse(patchJson, PatchProposal.class)
                    .map(proposal -> new PatchProposal(
                            proposal.repairTarget(),
                            proposal.rootCause(),
                            proposal.operations(),
                            proposal.testsToRun(),
                            true,
                            patchJson))
                    .orElseGet(() -> fallbackPatchProposal(patchJson));
            boolean hasOperations = state.patchProposal.operations() != null
                    && !state.patchProposal.operations().isEmpty();
            state.step("PatchParser", "patchJson", "operations="
                    + (state.patchProposal.operations() == null ? 0 : state.patchProposal.operations().size()),
                    hasOperations);
            eventHub.publish(state.sessionId, RepairStage.EXECUTING,
                    "Patch proposal parsed with operations="
                            + (state.patchProposal.operations() == null ? 0 : state.patchProposal.operations().size()));
            return state.patchProposal;
        }
    }

    public static final class PatchApplyOperator {
        private final SessionState state;
        private final PatchTools patchTools;
        private final RepairEventHub eventHub;

        private PatchApplyOperator(SessionState state, PatchTools patchTools, RepairEventHub eventHub) {
            this.state = state;
            this.patchTools = patchTools;
            this.eventHub = eventHub;
        }

        @Agent(name = "applyPatchProposal", description = "Apply safe exact-text patch operations",
                outputKey = "patchResult")
        public PatchResult applyPatchProposal(@V("patchProposal") PatchProposal patchProposal) {
            state.patchProposal = patchProposal;
            state.patchApplicationResult = patchTools.applyProposal(patchProposal);
            state.patchResult = toPatchResult(state.patchApplicationResult);
            state.step("PatchTools", state.patchResult.filePath(), state.patchResult.message(),
                    state.patchResult.success());
            eventHub.publish(state.sessionId, RepairStage.PATCHING, state.patchResult.message(),
                    Map.of("patch", state.patchResult));
            return state.patchResult;
        }
    }

    public static final class TestOperator {
        private final SessionState state;
        private final RunTestTools runTestTools;
        private final RepairProperties properties;
        private final RepairEventHub eventHub;

        private TestOperator(
                SessionState state,
                RunTestTools runTestTools,
                RepairProperties properties,
                RepairEventHub eventHub) {
            this.state = state;
            this.runTestTools = runTestTools;
            this.properties = properties;
            this.eventHub = eventHub;
        }

        @Agent(name = "runTargetTests", description = "Run target-service Maven tests", outputKey = "testResult")
        public TestExecutionResult runTargetTests(@V("patchResult") PatchResult patchResult) {
            state.testResult = runTestTools.runTargetServiceTests();
            state.step("RunTestTools", properties.getTargetProject().getTestCommand(),
                    "exitCode=" + state.testResult.exitCode()
                            + ", durationMs=" + state.testResult.durationMillis(),
                    state.testResult.success());
            int attempts = 1;
            while (!state.testResult.success()
                    && attempts < properties.getWorkflow().getMaxRepairAttempts()
                    && patchResult.success()) {
                attempts++;
                state.testResult = runTestTools.runTargetServiceTests();
                state.step("RunTestTools", "retry " + attempts,
                        "exitCode=" + state.testResult.exitCode()
                                + ", durationMs=" + state.testResult.durationMillis(),
                        state.testResult.success());
            }
            eventHub.publish(state.sessionId, RepairStage.TESTING, "Target-service tests completed",
                    Map.of("testResult", state.testResult));
            return state.testResult;
        }
    }

    public static final class ReviewOperator {
        private final SessionState state;
        private final RepairReviewerAgent reviewerAgent;
        private final RepairEventHub eventHub;

        private ReviewOperator(SessionState state, RepairReviewerAgent reviewerAgent, RepairEventHub eventHub) {
            this.state = state;
            this.reviewerAgent = reviewerAgent;
            this.eventHub = eventHub;
        }

        @Agent(name = "reviewRepair", description = "Review patch result, diff, and tests",
                outputKey = "reviewDecision")
        public ReviewDecision reviewRepair(@V("testResult") TestExecutionResult testResult) {
            state.execution = state.execution();
            eventHub.publish(state.sessionId, RepairStage.REVIEWING, "Reviewing diff and test result");
            state.reviewDecision = reviewerAgent.review(state.execution);
            state.step("ReviewAgent", "execution", state.reviewDecision.reason(),
                    state.reviewDecision.status() == ReviewStatus.PASS);
            eventHub.publish(state.sessionId, RepairStage.REVIEWING, state.reviewDecision.reason(),
                    Map.of("review", state.reviewDecision));
            return state.reviewDecision;
        }
    }

    public static final class CommitOperator {
        private final SessionState state;
        private final GitTools gitTools;
        private final RepairEventHub eventHub;

        private CommitOperator(SessionState state, GitTools gitTools, RepairEventHub eventHub) {
            this.state = state;
            this.gitTools = gitTools;
            this.eventHub = eventHub;
        }

        @Agent(name = "commitRepair", description = "Create repair branch and commit when review passes",
                outputKey = "gitCommitResult")
        public GitCommitResult commitRepair(@V("reviewDecision") ReviewDecision reviewDecision) {
            if (reviewDecision.status() != ReviewStatus.PASS) {
                state.gitCommitResult = new GitCommitResult(false, "", "", "Review did not pass");
                return state.gitCommitResult;
            }
            eventHub.publish(state.sessionId, RepairStage.COMMITTING, "Creating repair branch and commit");
            state.gitCommitResult = gitTools.commitAndPush(state.sessionId);
            state.step("GitTools", state.sessionId, state.gitCommitResult.message(), state.gitCommitResult.success());
            eventHub.publish(state.sessionId, RepairStage.COMMITTING, state.gitCommitResult.message(),
                    Map.of("git", state.gitCommitResult));
            return state.gitCommitResult;
        }
    }

    public static final class PullRequestOperator {
        private final SessionState state;
        private final GitHubTools gitHubTools;
        private final RepairEventHub eventHub;

        private PullRequestOperator(SessionState state, GitHubTools gitHubTools, RepairEventHub eventHub) {
            this.state = state;
            this.gitHubTools = gitHubTools;
            this.eventHub = eventHub;
        }

        @Agent(name = "createPullRequest", description = "Create GitHub PR after commit step",
                outputKey = "pullRequestResult")
        public PullRequestResult createPullRequest(@V("gitCommitResult") GitCommitResult gitCommitResult) {
            eventHub.publish(state.sessionId, RepairStage.PR_CREATED, "Creating GitHub pull request");
            state.pullRequestResult = gitHubTools.createPullRequest(
                    gitCommitResult.branchName(),
                    "fix: auto repair target-service validation",
                    buildPrBody(state.sessionId, state.plan, state.reviewDecision));
            state.step("GitHubTools", gitCommitResult.branchName(), state.pullRequestResult.message(),
                    state.pullRequestResult.success());
            eventHub.publish(state.sessionId, RepairStage.PR_CREATED, state.pullRequestResult.message(),
                    Map.of("pullRequest", state.pullRequestResult));
            return state.pullRequestResult;
        }
    }

    public static final class NotifyOperator {
        private final SessionState state;
        private final FeishuTools feishuTools;
        private final RepairEventHub eventHub;

        private NotifyOperator(SessionState state, FeishuTools feishuTools, RepairEventHub eventHub) {
            this.state = state;
            this.feishuTools = feishuTools;
            this.eventHub = eventHub;
        }

        @Agent(name = "sendNotification", description = "Send Feishu notification after PR step",
                outputKey = "notificationResult")
        public NotificationResult sendNotification(@V("pullRequestResult") PullRequestResult pullRequestResult) {
            eventHub.publish(state.sessionId, RepairStage.NOTIFIED, "Sending Feishu notification");
            state.notificationResult = feishuTools.sendRepairCard(
                    state.sessionId,
                    pullRequestResult,
                    state.plan.rootCauseHypothesis(),
                    state.reviewDecision.reason());
            state.step("FeishuTools", state.sessionId, state.notificationResult.message(),
                    state.notificationResult.success());
            eventHub.publish(state.sessionId, RepairStage.NOTIFIED, state.notificationResult.message(),
                    Map.of("notification", state.notificationResult));
            return state.notificationResult;
        }
    }

    public static final class ReflectOperator {
        private final SessionState state;
        private final RepairReflectionAgent reflectionAgent;
        private final RepairEventHub eventHub;

        private ReflectOperator(
                SessionState state, RepairReflectionAgent reflectionAgent, RepairEventHub eventHub) {
            this.state = state;
            this.reflectionAgent = reflectionAgent;
            this.eventHub = eventHub;
        }

        @Agent(name = "reflectRepair", description = "Generate repair reflection", outputKey = "reflection")
        public RepairReflection reflectRepair(@V("notificationResult") NotificationResult notificationResult) {
            eventHub.publish(state.sessionId, RepairStage.REFLECTING, "Generating repair reflection");
            state.reflection = reflectionAgent.reflect(
                    state.evidence, state.plan, state.execution(), state.reviewDecision);
            state.step("ReflectionAgent", state.sessionId, state.reflection.fixStrategy(), true);
            eventHub.publish(state.sessionId, RepairStage.REFLECTING, "Repair reflection generated",
                    Map.of("reflection", state.reflection));
            return state.reflection;
        }
    }

    public static final class RecordOperator {
        private final SessionState state;
        private final RepairRecordTools repairRecordTools;
        private final GitTools gitTools;
        private final RepairEventHub eventHub;

        private RecordOperator(
                SessionState state, RepairRecordTools repairRecordTools, GitTools gitTools, RepairEventHub eventHub) {
            this.state = state;
            this.repairRecordTools = repairRecordTools;
            this.gitTools = gitTools;
            this.eventHub = eventHub;
        }

        @Agent(name = "writeRepairRecord", description = "Persist repair record JSON", outputKey = "recordVersion")
        public Integer writeRepairRecord() {
            if (state.evidenceBundle == null || state.plan == null
                    || state.testResult == null || state.reviewDecision == null || state.reflection == null) {
                throw new IllegalStateException("Agentic repair state is incomplete");
            }
            state.diff = gitTools.readTargetDiff();
            RepairRecord record = new RepairRecord(
                    1,
                    state.sessionId,
                    state.startedAt,
                    Instant.now(),
                    state.evidenceBundle,
                    trim(state.evidence, 3000),
                    state.plan,
                    List.copyOf(state.steps),
                    state.patchProposal,
                    state.patchApplicationResult,
                    trim(state.diff, 6000),
                    state.testResult,
                    state.reviewDecision,
                    state.gitCommitResult,
                    state.pullRequestResult,
                    state.notificationResult,
                    state.reflection);
            repairRecordTools.writeRecord(record);
            state.recordWritten = true;
            eventHub.publish(state.sessionId, RepairStage.REFLECTING,
                    "Agentic repair record written", Map.of("recordVersion", record.recordVersion()));
            return record.recordVersion();
        }
    }

    private static final class RepairAgenticListener implements AgentListener {
        private final String sessionId;
        private final RepairEventHub eventHub;

        private RepairAgenticListener(String sessionId, RepairEventHub eventHub) {
            this.sessionId = sessionId;
            this.eventHub = eventHub;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            eventHub.publish(sessionId, RepairStage.EXECUTING,
                    "Agentic invoking " + request.agentName());
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            eventHub.publish(sessionId, RepairStage.EXECUTING,
                    "Agentic completed " + response.agentName());
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError error) {
            eventHub.publish(sessionId, RepairStage.ERROR,
                    "Agentic agent failed: " + error.agentName() + ": " + error.error().getMessage());
        }

        @Override
        public void beforeAgentToolExecution(BeforeAgentToolExecution toolExecution) {
            eventHub.publish(sessionId, RepairStage.EXECUTING,
                    "Agentic tool call: " + trim(String.valueOf(toolExecution.toolExecution()), 240));
        }

        @Override
        public void afterAgentToolExecution(AfterAgentToolExecution toolExecution) {
            eventHub.publish(sessionId, RepairStage.EXECUTING,
                    "Agentic tool completed: " + trim(String.valueOf(toolExecution.toolExecution()), 240));
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }

    public interface AgenticDiagnosisAgent {
        @Agent(name = "diagnoseRootCause", description = "Analyze evidence and identify likely root cause",
                outputKey = "diagnosis")
        @SystemMessage("""
                You are a Java Spring Boot repair diagnosis agent.
                Use the read-only tools only when needed.
                Prefer concrete exception names, business stack frames, and failing assertions.
                Return concise plain text.
                """)
        @UserMessage("""
                Evidence:
                {{evidence}}

                Diagnose the likely root cause.
                """)
        String diagnoseRootCause(@V("evidence") String evidence);
    }

    public interface AgenticPlanAgent {
        @Agent(name = "generateRepairPlan", description = "Generate strict JSON RepairPlan", outputKey = "planJson")
        @SystemMessage("""
                You are a Java Spring Boot repair planning agent.
                Return only strict JSON matching:
                {
                  "repairTarget": "short target",
                  "rootCauseHypothesis": "specific root cause",
                  "suspectedFiles": ["target-service/src/main/java/..."],
                  "steps": ["step 1", "step 2"],
                  "testCommand": "mvn -pl target-service test"
                }
                Only propose files under target-service/src/main or target-service/src/test.
                """)
        @UserMessage("""
                Evidence:
                {{evidence}}

                Diagnosis:
                {{diagnosis}}

                Generate the repair plan JSON.
                """)
        String generateRepairPlan(@V("evidence") String evidence, @V("diagnosis") String diagnosis);
    }

    public interface AgenticPatchAgent {
        @Agent(name = "generatePatchProposal", description = "Generate strict JSON PatchProposal",
                outputKey = "patchJson")
        @SystemMessage("""
                You are a Java Spring Boot repair executor.
                Return only strict JSON matching:
                {
                  "repairTarget": "same target as the plan",
                  "rootCause": "specific root cause",
                  "operations": [
                    {
                      "filePath": "target-service/src/main/java/...",
                      "oldText": "exact text currently present in the file",
                      "newText": "replacement text",
                      "reason": "why this change is needed"
                    }
                  ],
                  "testsToRun": ["mvn -pl target-service test"],
                  "modelGenerated": true,
                  "rawModelOutput": ""
                }
                oldText must be copied exactly from the source context.
                Keep oldText/newText to the smallest method-level or line-level replacement that fixes the bug.
                Do not modify agent-platform, root configs, secrets, scripts, or build files.
                """)
        @UserMessage("""
                Repair plan JSON:
                {{planJson}}

                Source context:
                {{sourceContext}}

                Generate the minimal safe patch proposal JSON.
                """)
        String generatePatchProposal(
                @V("planJson") String planJson,
                @V("sourceContext") String sourceContext);
    }
}
