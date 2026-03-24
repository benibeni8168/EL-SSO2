package org.keycloak.authentication.authenticators.conditional;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.risk.RiskEvaluator;
import org.keycloak.authentication.risk.RiskScore;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import org.jboss.logging.Logger;

/**
 * Conditional authenticator that evaluates contextual risk.
 * When the risk score exceeds the configured threshold, the condition matches
 * and the containing conditional sub-flow is executed (e.g., requiring step-up auth).
 */
public class ConditionalRiskAuthenticator implements ConditionalAuthenticator {

    private static final Logger logger = Logger.getLogger(ConditionalRiskAuthenticator.class);

    static final String RISK_THRESHOLD = "riskThreshold";
    static final String CONF_NEGATE = "negate";

    static final int DEFAULT_THRESHOLD = 50;

    public static final ConditionalRiskAuthenticator SINGLETON = new ConditionalRiskAuthenticator();

    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        AuthenticatorConfigModel authConfig = context.getAuthenticatorConfig();

        int threshold = DEFAULT_THRESHOLD;
        boolean negate = false;

        if (authConfig != null && authConfig.getConfig() != null) {
            try {
                threshold = Integer.parseInt(authConfig.getConfig().getOrDefault(RISK_THRESHOLD, String.valueOf(DEFAULT_THRESHOLD)));
                threshold = Math.max(0, Math.min(100, threshold));
            } catch (NumberFormatException e) {
                logger.warnf("Invalid risk threshold value, using default: %d", DEFAULT_THRESHOLD);
            }
            negate = Boolean.parseBoolean(authConfig.getConfig().get(CONF_NEGATE));
        }

        RiskEvaluator evaluator = context.getSession().getProvider(RiskEvaluator.class);
        if (evaluator == null) {
            logger.warn("No RiskEvaluator provider found. Condition evaluates to false.");
            return false;
        }

        RiskScore riskScore = evaluator.evaluate(context);
        boolean isHighRisk = riskScore.isHighRisk(threshold);

        logger.debugf("Risk evaluation: score=%d, threshold=%d, highRisk=%b, negate=%b, reason=%s",
                riskScore.getScore(), threshold, isHighRisk, negate, riskScore.getReason());

        return negate != isHighRisk;
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // Not used for conditional authenticators
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // Not used
    }

    @Override
    public void close() {
        // no-op
    }
}
