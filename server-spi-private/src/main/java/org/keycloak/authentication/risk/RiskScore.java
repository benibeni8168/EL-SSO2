package org.keycloak.authentication.risk;

/**
 * Represents a risk assessment score for an authentication attempt.
 */
public class RiskScore {

    private final int score;
    private final String reason;

    public RiskScore(int score, String reason) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        this.score = score;
        this.reason = reason;
    }

    /** Risk score from 0 (no risk) to 100 (maximum risk). */
    public int getScore() {
        return score;
    }

    /** Human-readable reason for the risk score. */
    public String getReason() {
        return reason;
    }

    public boolean isHighRisk(int threshold) {
        return score >= threshold;
    }

    @Override
    public String toString() {
        return "RiskScore{score=" + score + ", reason='" + reason + "'}";
    }
}
