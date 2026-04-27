package org.example.agentaiops.repair.agentic;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import java.time.Instant;
import java.util.Map;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.llm.RepairChatModelProvider;
import org.example.agentaiops.llm.StructuredJsonParser;
import org.example.agentaiops.repair.agent.EvidenceAgent;
import org.example.agentaiops.repair.agent.RepairReflectionAgent;
import org.example.agentaiops.repair.agent.RepairReviewerAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticDiagnosisAgent;
import org.example.agentaiops.repair.agentic.agents.AgenticPatchAgent;
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
import org.example.agentaiops.repair.model.RepairStage;
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

    /** Returns true when the Agentic workflow has a configured model. */
    public boolean available() {
        return chatModelProvider.available();
    }

    /** Runs the supervisor-controlled repair workflow for one session. */
    public void run(String sessionId, Instant startedAt) {
        if (!available()) {
            throw new IllegalStateException("LangChain4j agentic repair requires a configured LLM");
        }

        AgenticRepairState state = new AgenticRepairState(sessionId, startedAt);
        AgenticReadOnlyTools readOnlyTools = new AgenticReadOnlyTools(readLogTools, readCodeTools);
        RepairAgenticListener listener = new RepairAgenticListener(state, eventHub);

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
                        new PlanParserOperator(state, jsonParser, eventHub),
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
        long durationMillis = state.timing().durationMillis();
        eventHub.publish(sessionId, RepairStage.COMPLETED, "Repair workflow completed",
                Map.of(
                        "recordVersion", 1,
                        "mode", "langchain4j-agentic",
                        "stepName", "repairWorkflow",
                        "durationMillis", durationMillis));
    }

    private String supervisorContext() {
        return """
                You are the autonomous repair supervisor for a Java Spring Boot incident.
                Assume the role of an incident commander and senior maintainer: form hypotheses,
                delegate to specialist agents, inspect their outputs, and drive the repair to a safe
                completion. Do not behave like a passive fixed script. Use the available sub-agents
                only when their role is needed and avoid repeating an agent once it has produced a
                valid output.

                Specialist roles:
                - collectEvidence is the evidence collector. Use it first to gather traceback,
                  candidate files, tests, and source snippets.
                - diagnoseRootCause is the root-cause diagnostician. Use it after evidence exists
                  to identify the failing boundary, application stack frame, and likely contract bug.
                - generateRepairPlan is the repair planner. Use it to convert the diagnosis into
                  strict JSON with target files, repair steps, and validation command.
                - parseRepairPlan is the schema gate. Use it immediately after plan JSON is produced;
                  stop on invalid JSON.
                - prepareSourceContext is the source curator. Use it after a valid plan exists to
                  provide bounded, exact snippets for patch generation.
                - generatePatchProposal is the patch author. Use it only after source context exists
                  and require exact oldText/newText replacements.
                - parsePatchProposal is the patch schema gate. Use it immediately after patch JSON is
                  produced; stop on invalid JSON or empty operations.
                - applyPatchProposal, runTargetTests, and reviewRepair are the controlled execution
                  and safety gates. Use them after a parsed patch exists.
                - commitRepair, createPullRequest, and sendNotification are release handoff agents.
                  They respect disabled-mode configuration and may report skipped actions.
                - reflectRepair and writeRepairRecord capture the learning and final audit trail.

                Dependency rules:
                - Evidence must exist before diagnosis, planning, or patching.
                - A parsed RepairPlan must exist before source context and patch generation.
                - A parsed PatchProposal must exist before applying code changes.
                - Tests and review must run after patch application and before external handoff.
                - A repair record must be written before claiming completion.

                Safety:
                - Only read target-service source and logs.
                - Only patch target-service/src/main or target-service/src/test through PatchTools.
                - GitHub and Feishu agents are allowed in the workflow, but they obey disabled-mode config.
                - If a schema gate or safety gate fails, stop and surface the error instead of
                  inventing a fallback patch.
                """;
    }

    private String supervisorRequest() {
        return """
                Repair the current target-service failure as an autonomous multi-agent supervisor.
                Delegate to the specialist agents according to their roles and dependency rules.
                Keep the run focused: do not re-invoke agents whose outputs are already valid.
                The final result must be a safe patch, validation result, repair reflection, and
                repair record written to repair-records.
                """;
    }
}
