package org.example.agentaiops.repair.model;

import java.util.List;

/** File-level summary for a parsed repair diff. */
public record RepairDiffFile(
        String filePath,
        String oldPath,
        String newPath,
        String status,
        int additions,
        int deletions,
        List<RepairDiffHunk> hunks) {
}
