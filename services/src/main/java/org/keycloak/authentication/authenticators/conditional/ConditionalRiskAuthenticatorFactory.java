package org.keycloak.authentication.authenticators.conditional;

import java.util.Arrays;
import java.util.List;

import org.keycloak.Config.Scope;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory for the risk-based conditional authenticator.
 * Allows configuring a risk threshold to trigger step-up authentication.
 */
public class ConditionalRiskAuthenticatorFactory implements ConditionalAuthenticatorFactory {

    public static final String PROVIDER_ID = "conditional-risk";

    @Override
    public void init(Scope config) {
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

    @Override
    public String getDisplayType() {
        return "Condition - risk-based";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    private static final Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Flow is executed when contextual risk score exceeds the configured threshold. "
                + "Evaluates failed logins, IP changes, and request characteristics to compute risk.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty threshold = new ProviderConfigProperty();
        threshold.setType(ProviderConfigProperty.STRING_TYPE);
        threshold.setName(ConditionalRiskAuthenticator.RISK_THRESHOLD);
        threshold.setLabel("Risk threshold");
        threshold.setDefaultValue("50");
        threshold.setHelpText("Risk score threshold (0-100). If the computed risk score is equal to or above this value, "
                + "the condition matches and the sub-flow is executed. Default: 50");

        ProviderConfigProperty negateOutput = new ProviderConfigProperty();
        negateOutput.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        negateOutput.setName(ConditionalRiskAuthenticator.CONF_NEGATE);
        negateOutput.setLabel("Negate output");
        negateOutput.setHelpText("Apply a NOT to the check result. When true, the condition evaluates to true when risk is BELOW the threshold.");

        return Arrays.asList(threshold, negateOutput);
    }

    @Override
    public ConditionalAuthenticator getSingleton() {
        return ConditionalRiskAuthenticator.SINGLETON;
    }
}
