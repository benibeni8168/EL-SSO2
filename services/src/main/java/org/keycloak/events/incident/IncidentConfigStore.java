package org.keycloak.events.incident;

import org.keycloak.models.RealmModel;
import org.keycloak.util.JsonSerialization;

import org.jboss.logging.Logger;

public final class IncidentConfigStore {

    private static final Logger logger = Logger.getLogger(IncidentConfigStore.class);

    public static final String REALM_ATTR = "incident.config";

    private IncidentConfigStore() {
    }

    public static IncidentConfigRepresentation readConfig(RealmModel realm) {
        String raw = realm.getAttribute(REALM_ATTR);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JsonSerialization.readValue(raw, IncidentConfigRepresentation.class);
        } catch (Exception e) {
            logger.warnf("Failed to parse incident config for realm '%s' — treating as not configured: %s",
                    realm.getId(), e.getMessage());
            return null;
        }
    }

    public static void writeConfig(RealmModel realm, IncidentConfigRepresentation config) {
        try {
            if (config == null) {
                realm.removeAttribute(REALM_ATTR);
            } else {
                realm.setAttribute(REALM_ATTR, JsonSerialization.writeValueAsString(config));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist incident config", e);
        }
    }
}
