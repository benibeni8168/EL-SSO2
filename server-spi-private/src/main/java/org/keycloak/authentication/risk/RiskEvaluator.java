package org.keycloak.authentication.risk;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.provider.Provider;

/**
 * Evaluates contextual risk for an authentication attempt.
 */
public interface RiskEvaluator extends Provider {

    /**
     * Evaluate the risk of the current authentication attempt.
     *
     * @param context the authentication flow context
     * @return a RiskScore between 0 (safe) and 100 (dangerous)
     */
    RiskScore evaluate(AuthenticationFlowContext context);
}
