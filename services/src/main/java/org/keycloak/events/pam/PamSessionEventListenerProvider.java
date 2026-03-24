package org.keycloak.events.pam;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.pam.PamProvider;

import org.jboss.logging.Logger;

public class PamSessionEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(PamSessionEventListenerProvider.class);

    private final KeycloakSession session;

    public PamSessionEventListenerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getRealmId() == null) {
            return;
        }

        RealmModel realm = session.realms().getRealm(event.getRealmId());
        if (realm == null) {
            return;
        }

        PamConfigRepresentation config = PamConfigStore.readConfig(realm);
        if (config == null || !config.isEnabled() || !config.isNotifySessions()) {
            return;
        }

        String eventTypeName = null;
        if (event.getType() == EventType.LOGIN) {
            eventTypeName = "SESSION_CREATED";
        } else if (event.getType() == EventType.LOGOUT) {
            eventTypeName = "SESSION_ENDED";
        }

        if (eventTypeName == null) {
            return;
        }

        PamProvider pamProvider = session.getProvider(PamProvider.class);
        if (pamProvider == null) {
            logger.warn("No PamProvider found — skipping session notification");
            return;
        }

        String username = getUsername(realm, event.getUserId());
        pamProvider.notifySession(
                eventTypeName,
                event.getSessionId(),
                event.getUserId(),
                username,
                event.getClientId(),
                event.getRealmId(),
                event.getIpAddress());
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    private String getUsername(RealmModel realm, String userId) {
        if (userId == null) {
            return "unknown";
        }
        try {
            UserModel user = session.users().getUserById(realm, userId);
            return user != null ? user.getUsername() : "unknown";
        } catch (Exception e) {
            logger.debugf("Failed to resolve username for userId %s: %s", userId, e.getMessage());
            return "unknown";
        }
    }
}
