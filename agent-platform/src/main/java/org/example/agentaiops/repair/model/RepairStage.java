package org.example.agentaiops.repair.model;

public enum RepairStage {
    DETECTING("detecting"),
    PLANNING("planning"),
    EXECUTING("executing"),
    PATCHING("patching"),
    TESTING("testing"),
    REVIEWING("reviewing"),
    COMMITTING("committing"),
    PR_CREATED("pr_created"),
    NOTIFIED("notified"),
    REFLECTING("reflecting"),
    COMPLETED("completed"),
    ERROR("error");

    private final String wireName;

    RepairStage(String wireName) {
        this.wireName = wireName;
    }

    /** Returns the stable stage name exposed through SSE. */
    public String wireName() {
        return wireName;
    }
}
