package org.example.agentaiops.demo;

/** State exposed by the one-click demo scenario API. */
public enum DemoScenarioStage {
    CREATED,
    WAITING_FOR_TARGET_RESTART,
    RUNNING,
    FIXED,
    FAILED,
    ERROR
}
