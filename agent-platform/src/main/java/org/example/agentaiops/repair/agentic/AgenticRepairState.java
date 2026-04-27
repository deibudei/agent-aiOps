package org.example.agentaiops.repair.agentic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.example.agentaiops.repair.model.EvidenceBundle;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PatchApplicationResult;
import org.example.agentaiops.repair.model.PatchProposal;
import org.example.agentaiops.repair.model.PatchResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairExecutionResult;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairStepResult;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.TestExecutionResult;

/** Mutable state shared by one LangChain4j Agentic repair run. */
public final class AgenticRepairState {

    public final String sessionId;
    public final Instant startedAt;
    public final List<RepairStepResult> steps = new ArrayList<>();
    public EvidenceBundle evidenceBundle;
    public String evidence;
    public String diagnosis;
    public String planJson;
    public RepairPlan plan;
    public String sourceContext;
    public String patchJson;
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
    public boolean recordWritten;

    public AgenticRepairState(String sessionId, Instant startedAt) {
        this.sessionId = sessionId;
        this.startedAt = startedAt;
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
}
