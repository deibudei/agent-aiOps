package org.example.agentaiops.repair.tool;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RepairToolRegistry {

    /** Lists tool names that are exposed to planner prompts and records. */
    public List<String> toolNames() {
        return List.of(
                "ReadLogTools",
                "ReadCodeTools",
                "PatchTools",
                "RunTestTools",
                "GitTools",
                "GitHubTools",
                "FeishuTools",
                "RepairRecordTools");
    }
}
