package org.example.agentaiops.repair.model;

import java.util.List;

/** API response containing compact summaries of repair records. */
public record RepairRecordIndex(int count, List<RepairRecordSummary> records) {
}
