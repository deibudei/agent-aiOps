package org.example.agentaiops.repair.model;

public class RepairRunRequest {

    private String sessionId;

    /** Returns the requested session id, or null when the server should generate one. */
    public String getSessionId() {
        return sessionId;
    }

    /** Sets the requested repair session id from the API body. */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
