package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.example.agentaiops.repair.model.GitCommitResult;
import org.example.agentaiops.repair.model.NotificationResult;
import org.example.agentaiops.repair.model.PullRequestResult;
import org.example.agentaiops.repair.model.RepairModelUsage;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.model.RepairReflection;
import org.example.agentaiops.repair.model.RepairStepTiming;
import org.example.agentaiops.repair.model.RepairTiming;
import org.example.agentaiops.repair.model.ReviewDecision;
import org.example.agentaiops.repair.model.ReviewStatus;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepairRecordToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void writesTimingToJsonAndMarkdown() throws Exception {
        RepairProperties properties = new RepairProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        RepairRecordTools tools = new RepairRecordTools(new ToolPolicy(properties), new ObjectMapper());

        ToolExecutionResult result = tools.writeRecord(recordWithTiming());

        assertThat(result.success()).isTrue();
        String json = Files.readString(tempDir.resolve("repair-records/timing-session.json"));
        String markdown = Files.readString(tempDir.resolve("repair-records/timing-session.md"));
        assertThat(json).contains("\"timing\"");
        assertThat(json).contains("\"durationMillis\" : 1234");
        assertThat(json).contains("\"modelUsage\"");
        assertThat(json).contains("\"totalTokenCount\" : 120");
        assertThat(markdown).contains("## Timing");
        assertThat(markdown).contains("## Model Usage");
        assertThat(markdown).contains("| collectEvidence | 10 | true |");
        assertThat(markdown).contains("| generateRepairPlan | PLAN | deepseek-v4-flash |");
    }

    private RepairRecord recordWithTiming() {
        Instant started = Instant.parse("2026-04-27T09:00:00Z");
        RepairTiming timing = new RepairTiming(
                started,
                started.plusMillis(1234),
                1234,
                List.of(new RepairStepTiming(
                        "collectEvidence",
                        started,
                        started.plusMillis(10),
                        10,
                        true,
                        "evidence collected")),
                List.of(new RepairModelUsage(
                        "generateRepairPlan",
                        "PLAN",
                        "deepseek-v4-flash",
                        "deepseek-v4-flash",
                        1,
                        100,
                        20,
                        120)));
        return new RepairRecord(
                1,
                "timing-session",
                started,
                started.plusMillis(1234),
                null,
                "traceback",
                new RepairPlan(
                        "target",
                        "root cause",
                        List.of("target-service/src/main/java/App.java"),
                        List.of("patch"),
                        "mvn -pl target-service test"),
                List.of(),
                null,
                null,
                "diff",
                new TestExecutionResult(0, "ok", "", 10, false),
                new ReviewDecision(ReviewStatus.PASS, "ok", "low", List.of("target-service/src/main/java/App.java")),
                new GitCommitResult(false, "", "", "disabled"),
                new PullRequestResult(false, "", "disabled"),
                new NotificationResult(false, "disabled"),
                new RepairReflection("root cause", "evidence", "fix", "tests", "lesson", List.of("hint")),
                timing);
    }
}
