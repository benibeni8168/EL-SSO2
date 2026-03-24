package org.keycloak.authentication.authenticators.pam;

import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.events.pam.PamConfigRepresentation;
import org.keycloak.events.pam.PamConfigStore;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.pam.PamCheckResult;
import org.keycloak.pam.PamProvider;
import org.keycloak.services.messages.Messages;

import org.jboss.logging.Logger;

public class PamCheckAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(PamCheckAuthenticator.class);

    public static final PamCheckAuthenticator SINGLETON = new PamCheckAuthenticator();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        PamConfigRepresentation config = PamConfigStore.readConfig(context.getRealm());

        if (config == null || !config.isEnabled()) {
            context.success();
            return;
        }

        String clientId = context.getAuthenticationSession().getClient().getClientId();

        if (!config.isAllClientsPrivileged()) {
            if (config.getPrivilegedClients() == null || !config.getPrivilegedClients().contains(clientId)) {
                context.success();
                return;
            }
        }

        String userId = context.getUser() != null ? context.getUser().getId() : "unknown";
        String username = context.getUser() != null ? context.getUser().getUsername() : "unknown";
        String realmId = context.getRealm().getId();
        String ipAddress = context.getConnection().getRemoteAddr();

        PamProvider pamProvider = context.getSession().getProvider(PamProvider.class);
        if (pamProvider == null) {
            logger.warn("No PamProvider found — allowing access by default");
            context.success();
            return;
        }

        PamCheckResult result = pamProvider.checkAccess(userId, username, clientId, realmId, ipAddress);

        if (result.isAllowed()) {
            context.success();
        } else {
            logger.infof("PAM denied access for user '%s' to client '%s': %s", username, clientId, result.getReason());
            context.getEvent().error(Errors.ACCESS_DENIED);
            Response challenge = context.form()
                    .setError(Messages.ACCESS_DENIED)
                    .createErrorPage(Response.Status.UNAUTHORIZED);
            context.failureChallenge(AuthenticationFlowError.ACCESS_DENIED, challenge);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // no-op
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
