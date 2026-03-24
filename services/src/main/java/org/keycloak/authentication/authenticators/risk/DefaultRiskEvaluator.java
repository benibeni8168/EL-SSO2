package org.keycloak.authentication.authenticators.risk;

import jakarta.ws.rs.core.MultivaluedMap;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.risk.RiskEvaluator;
import org.keycloak.authentication.risk.RiskScore;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserLoginFailureModel;
import org.keycloak.models.UserModel;

import org.jboss.logging.Logger;

/**
 * Default risk evaluator that scores authentication risk based on:
 * - Failed login history
 * - IP address change from last known failure IP
 * - Brute force protection status
 */
public class DefaultRiskEvaluator implements RiskEvaluator {

    private static final Logger logger = Logger.getLogger(DefaultRiskEvaluator.class);

    private final KeycloakSession session;

    public DefaultRiskEvaluator(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public RiskScore evaluate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            return new RiskScore(0, "No user context available");
        }

        RealmModel realm = context.getRealm();
        String remoteAddr = context.getConnection() != null ? context.getConnection().getRemoteAddr() : null;

        int totalScore = 0;
        StringBuilder reasons = new StringBuilder();

        // Signal 1: Failed login count
        int failureScore = evaluateFailedLogins(realm, user.getId());
        if (failureScore > 0) {
            totalScore += failureScore;
            reasons.append("failed_logins;");
        }

        // Signal 2: IP address mismatch with last known failure IP
        int ipScore = evaluateIpMismatch(realm, user.getId(), remoteAddr);
        if (ipScore > 0) {
            totalScore += ipScore;
            reasons.append("ip_mismatch;");
        }

        // Signal 3: Missing or suspicious headers
        int headerScore = evaluateHeaders(context);
        if (headerScore > 0) {
            totalScore += headerScore;
            reasons.append("suspicious_headers;");
        }

        // Cap at 100
        totalScore = Math.min(totalScore, 100);

        logger.debugf("Risk evaluation for user %s from IP %s: score=%d, reasons=%s",
                user.getUsername(), remoteAddr, totalScore, reasons);

        return new RiskScore(totalScore, reasons.toString());
    }

    private int evaluateFailedLogins(RealmModel realm, String userId) {
        UserLoginFailureModel failure = session.loginFailures().getUserLoginFailure(realm, userId);
        if (failure == null) {
            return 0;
        }

        int numFailures = failure.getNumFailures();
        if (numFailures == 0) {
            return 0;
        }

        // Scale: 1-2 failures = 10, 3-5 = 25, 6-10 = 40, 10+ = 50
        if (numFailures <= 2) return 10;
        if (numFailures <= 5) return 25;
        if (numFailures <= 10) return 40;
        return 50;
    }

    private int evaluateIpMismatch(RealmModel realm, String userId, String currentIp) {
        // Cannot evaluate IP mismatch without current IP
        if (currentIp == null) {
            return 0;
        }

        UserLoginFailureModel failure = session.loginFailures().getUserLoginFailure(realm, userId);
        if (failure == null || failure.getLastIPFailure() == null) {
            return 0;
        }

        // If current IP differs from the last failure IP, it may indicate
        // a different actor or a distributed attack
        if (!currentIp.equals(failure.getLastIPFailure()) && failure.getNumFailures() > 0) {
            return 20;
        }
        return 0;
    }

    private int evaluateHeaders(AuthenticationFlowContext context) {
        try {
            MultivaluedMap<String, String> headers =
                    context.getHttpRequest().getHttpHeaders().getRequestHeaders();

            // Check for missing User-Agent (common in automated attacks)
            if (headers == null) {
                return 0;
            }
            String userAgent = headers.getFirst("User-Agent");
            if (userAgent == null || userAgent.isEmpty()) {
                return 15;
            }
        } catch (Exception e) {
            logger.debug("Could not evaluate request headers", e);
        }
        return 0;
    }

    @Override
    public void close() {
        // no-op
    }
}
