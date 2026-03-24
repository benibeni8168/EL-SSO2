package org.keycloak.authentication.authenticators.risk;

import org.keycloak.Config;
import org.keycloak.authentication.risk.RiskEvaluator;
import org.keycloak.authentication.risk.RiskEvaluatorFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class DefaultRiskEvaluatorFactory implements RiskEvaluatorFactory {

    public static final String PROVIDER_ID = "default";

    @Override
    public RiskEvaluator create(KeycloakSession session) {
        return new DefaultRiskEvaluator(session);
    }

    @Override
    public void init(Config.Scope config) {
        // no-op
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
