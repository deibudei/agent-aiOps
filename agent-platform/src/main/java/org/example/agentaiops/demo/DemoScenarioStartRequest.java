package org.example.agentaiops.demo;

/** Request body used to start a one-click demo scenario. */
public class DemoScenarioStartRequest {

    private String sessionId;
    private String faultType;

    /** Returns the requested session id, or null when the server should generate one. */
    public String getSessionId() {
        return sessionId;
    }

    /** Sets the requested session id. */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** Returns the demo fault type to inject. */
    public String getFaultType() {
        return faultType;
    }

    /** Sets the demo fault type to inject. */
    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }
}
