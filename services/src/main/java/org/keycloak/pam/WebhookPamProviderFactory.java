package org.keycloak.pam;

import org.keycloak.Config;
import org.keycloak.events.pam.PamConfigRepresentation;
import org.keycloak.events.pam.PamConfigStore;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

public class WebhookPamProviderFactory implements PamProviderFactory {

    public static final String PROVIDER_ID = "webhook";

    @Override
    public PamProvider create(KeycloakSession session) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            return new NoOpPamProvider();
        }
        PamConfigRepresentation config = PamConfigStore.readConfig(realm);
        if (config == null || !config.isEnabled()) {
            return new NoOpPamProvider();
        }
        return new WebhookPamProvider(config);
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

    private static class NoOpPamProvider implements PamProvider {

        @Override
        public PamCheckResult checkAccess(String userId, String username, String clientId, String realmId, String ipAddress) {
            return new PamCheckResult(true, "PAM not configured", null);
        }

        @Override
        public void notifySession(String event, String sessionId, String userId, String username, String clientId, String realmId, String ipAddress) {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
