package org.example.agentaiops.repair.agentic;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.example.agentaiops.repair.model.EvidenceBundle;
import org.example.agentaiops.repair.model.DiagnosisResult;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairStepResult;
import org.example.agentaiops.repair.model.RepairTiming;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.TestExecutionResult;

/** Mutable state shared by one LangChain4j Agentic repair run. */
public final class AgenticRepairState {

    public final String sessionId;
    public final Instant startedAt;
    public final List<RepairStepResult> steps = new ArrayList<>();
    private final RepairTimingCollector timingCollector;
    private final Deque<List<PatchSnapshot>> rollbackSnapshots = new ArrayDeque<>();
    public EvidenceBundle evidenceBundle;
    public String evidence;
    public DiagnosisResult diagnosis;
    public RepairPlan plan;
    public String sourceContext;
    public PatchProposal patchProposal;
    public PatchApplicationResult patchApplicationResult;
    public PatchResult patchResult;
    public TestExecutionResult testResult;
    public RepairExecutionResult execution;
    public ReviewDecision reviewDecision;
    public GitCommitResult gitCommitResult;
    public PullRequestResult pullRequestResult;
    public NotificationResult notificationResult;
    public RepairReflection reflection;
    public String diff;
    public String supervisorSummary;
    public int patchAttempts;
    public boolean recordWritten;

    public AgenticRepairState(String sessionId, Instant startedAt) {
        this.sessionId = sessionId;
        this.startedAt = startedAt;
        this.timingCollector = new RepairTimingCollector(startedAt);
        this.gitCommitResult = new GitCommitResult(false, "", "", "Agent did not run commit step");
        this.pullRequestResult = new PullRequestResult(false, "", "Agent did not run PR step");
        this.notificationResult = new NotificationResult(false, "Agent did not run notification step");
    }

    public RepairExecutionResult execution() {
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

    public void step(String toolName, String input, String output, boolean success) {
        steps.add(new RepairStepResult(toolName, input, output, success));
        execution = null;
    }

    public void beginTiming(String stepName) {
        timingCollector.begin(stepName);
    }

    public void endTiming(String stepName, boolean success, String summary) {
        timingCollector.end(stepName, success, summary);
    }

    public RepairModelUsage recordModelUsage(
            String stepName, String role, String configuredModel, ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        return timingCollector.recordModelUsage(
                stepName,
                role,
                configuredModel,
                chatResponse.modelName(),
                chatResponse.tokenUsage());
    }

    public RepairTiming timing() {
        return timingCollector.snapshot();
    }

    /** Records a stack of file snapshots so the most recent apply can be rolled back. */
    public synchronized void recordRollbackSnapshots(List<PatchSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        rollbackSnapshots.push(List.copyOf(snapshots));
    }

    /** Returns the most recent snapshot list and removes it from the rollback stack. */
    public synchronized List<PatchSnapshot> popRollbackSnapshots() {
        return rollbackSnapshots.isEmpty() ? null : rollbackSnapshots.pop();
    }
}
