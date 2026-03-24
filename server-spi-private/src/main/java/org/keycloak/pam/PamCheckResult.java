package org.keycloak.pam;

public class PamCheckResult {

    private boolean allowed;
    private String reason;
    private String pamSessionRef;

    public PamCheckResult(boolean allowed, String reason, String pamSessionRef) {
        this.allowed = allowed;
        this.reason = reason;
        this.pamSessionRef = pamSessionRef;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }

    public String getPamSessionRef() {
        return pamSessionRef;
    }
}
