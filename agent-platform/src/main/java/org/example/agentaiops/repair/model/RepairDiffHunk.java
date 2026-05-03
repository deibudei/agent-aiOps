package org.example.agentaiops.repair.model;

import java.util.List;

/** A parsed hunk from a unified git diff. */
public record RepairDiffHunk(
        String header,
        int oldStart,
        int oldLines,
        int newStart,
        int newLines,
        List<RepairDiffLine> lines) {
}
