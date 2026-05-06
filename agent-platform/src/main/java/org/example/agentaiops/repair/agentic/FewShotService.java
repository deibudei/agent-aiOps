package org.example.agentaiops.repair.agentic;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.example.agentaiops.repair.model.RepairDiffFile;
import org.example.agentaiops.repair.model.RepairPlan;
import org.example.agentaiops.repair.model.RepairRecord;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides dynamic few-shot examples from past successful repair records.
 * Injects the top-N most similar repairs into the Patch Agent prompt,
 * replacing the need for hardcoded few-shot examples.
 */
@Service
public class FewShotService {

    private static final Logger log = LoggerFactory.getLogger(FewShotService.class);
    private static final int MAX_EXAMPLES = 3;
    private static final Pattern EXCEPTION_PATTERN =
            Pattern.compile("\\b([\\w$]+(?:Exception|Error))\\b");
    private static final Pattern FILE_PATTERN =
            Pattern.compile("([\\w$]+\\.java)\\b");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolPolicy toolPolicy;

    public FewShotService(ToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

    /**
     * Retrieves the top-N most relevant past repair records as formatted few-shot text.
     * Returns an empty string if no records are available.
     */
    public String loadFewShotExamples(String currentEvidence, String currentPlanTarget) {
        List<RepairRecord> pastRecords = loadSuccessfulRecords();
        if (pastRecords.isEmpty()) {
            return "";
        }

        List<RepairRecord> ranked = rankBySimilarity(pastRecords, currentEvidence, currentPlanTarget);
        List<RepairRecord> topN = ranked.subList(0, Math.min(MAX_EXAMPLES, ranked.size()));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < topN.size(); i++) {
            sb.append("Historical example ").append(i + 1).append(":\n");
            sb.append(formatAsFewShot(topN.get(i)));
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<RepairRecord> loadSuccessfulRecords() {
        List<RepairRecord> records = new ArrayList<>();
        try {
            Path recordsDir = toolPolicy.homeWorkspaceRoot().resolve("repair-records");
            if (!Files.exists(recordsDir)) {
                return records;
            }
            try (Stream<Path> files = Files.list(recordsDir)) {
                files.filter(f -> f.toString().endsWith(".json"))
                        .forEach(f -> {
                            try {
                                RepairRecord record = objectMapper.readValue(f.toFile(), RepairRecord.class);
                                if (record.outcome() != null && "FIXED".equals(record.outcome().name())) {
                                    records.add(record);
                                }
                            } catch (IOException e) {
                                log.debug("Skipping unreadable record: {}", f, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.debug("Could not scan repair-records: {}", e.getMessage());
        }
        return records;
    }

    private List<RepairRecord> rankBySimilarity(List<RepairRecord> records,
                                                 String currentEvidence,
                                                 String currentPlanTarget) {
        String lowerEvidence = currentEvidence != null ? currentEvidence.toLowerCase() : "";
        String lowerTarget = currentPlanTarget != null ? currentPlanTarget.toLowerCase() : "";

        return records.stream()
                .sorted(Comparator.<RepairRecord>comparingInt(r -> {
                    RepairPlan plan = r.plan();
                    String summary = (r.tracebackSummary() + " "
                            + (plan != null ? (plan.repairTarget() + " " + plan.rootCauseHypothesis()) : ""))
                            .toLowerCase();
                    int score = 0;
                    for (String token : extractExceptionNames(lowerEvidence)) {
                        if (summary.contains(token)) score += 5;
                    }
                    for (String token : extractFileNames(lowerEvidence)) {
                        if (summary.contains(token)) score += 3;
                    }
                    for (String word : lowerTarget.split("\\s+")) {
                        if (word.length() > 2 && summary.contains(word)) score += 1;
                    }
                    return -score;
                }).reversed())
                .toList();
    }

    private String formatAsFewShot(RepairRecord record) {
        RepairPlan plan = record.plan();
        StringBuilder sb = new StringBuilder();
        sb.append("Session: ").append(record.sessionId()).append("\n");
        if (record.tracebackSummary() != null) {
            sb.append("Evidence: ").append(record.tracebackSummary()).append("\n");
        }
        if (plan != null) {
            sb.append("Repair target: ").append(plan.repairTarget()).append("\n");
            sb.append("Root cause: ").append(plan.rootCauseHypothesis()).append("\n");
        }
        if (record.diffFiles() != null && !record.diffFiles().isEmpty()) {
            List<String> fileNames = record.diffFiles().stream()
                    .map(RepairDiffFile::filePath).toList();
            sb.append("Changed files: ").append(String.join(", ", fileNames)).append("\n");
        }
        if (record.diffSummary() != null && !record.diffSummary().isBlank()) {
            sb.append("Patch: ").append(record.diffSummary()).append("\n");
        }
        return sb.toString();
    }

    private List<String> extractExceptionNames(String text) {
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher m = EXCEPTION_PATTERN.matcher(text);
        while (m.find()) {
            names.add(m.group(1).toLowerCase());
        }
        return names;
    }

    private List<String> extractFileNames(String text) {
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher m = FILE_PATTERN.matcher(text);
        while (m.find()) {
            names.add(m.group(1).toLowerCase());
        }
        return names;
    }
}
