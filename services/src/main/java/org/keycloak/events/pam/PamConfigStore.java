package org.keycloak.events.pam;

import org.keycloak.models.RealmModel;
import org.keycloak.util.JsonSerialization;

import org.jboss.logging.Logger;

public final class PamConfigStore {

    private static final Logger logger = Logger.getLogger(PamConfigStore.class);

    public static final String REALM_ATTR = "pam.config";

    private PamConfigStore() {
    }

    public static PamConfigRepresentation readConfig(RealmModel realm) {
        String raw = realm.getAttribute(REALM_ATTR);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JsonSerialization.readValue(raw, PamConfigRepresentation.class);
        } catch (Exception e) {
            logger.warnf("Failed to parse PAM config for realm '%s' — treating as not configured: %s",
                    realm.getId(), e.getMessage());
            return null;
        }
    }

    public static void writeConfig(RealmModel realm, PamConfigRepresentation config) {
        try {
            if (config == null) {
                realm.removeAttribute(REALM_ATTR);
            } else {
                realm.setAttribute(REALM_ATTR, JsonSerialization.writeValueAsString(config));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist PAM config", e);
        }
    }
}
