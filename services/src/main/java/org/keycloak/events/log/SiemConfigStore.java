package org.keycloak.events.log;

import java.util.HashMap;
import java.util.HashSet;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.util.JsonSerialization;

public final class SiemConfigStore {

    public static final String REALM_ATTR = "siem.config";
    public static final String GLOBAL_ATTR = "siem.global.config";
    public static final String LISTENER_ID = LogExportEventListenerProviderFactory.PROVIDER_ID;

    private SiemConfigStore() {
    }

    public static SiemConfigRepresentation readRealmConfig(RealmModel realm) {
        return normalize(read(realm, REALM_ATTR));
    }

    public static SiemConfigRepresentation readGlobalConfig(RealmModel realm) {
        return normalize(read(realm, GLOBAL_ATTR));
    }

    public static void writeRealmConfig(RealmModel realm, SiemConfigRepresentation config) {
        write(realm, REALM_ATTR, config);
    }

    public static void writeGlobalConfig(RealmModel realm, SiemConfigRepresentation config) {
        write(realm, GLOBAL_ATTR, config);
    }

    public static SiemConfigRepresentation resolveEffectiveConfig(KeycloakSession session, String realmId,
            LogExportConfig legacyConfig, boolean legacyListenerConfigured) {
        RealmModel realm = resolveRealm(session, realmId);
        if (realm == null) {
            return null;
        }
        return resolveEffectiveConfig(session, realm, legacyConfig, legacyListenerConfigured);
    }

    public static SiemConfigRepresentation resolveEffectiveConfig(KeycloakSession session, RealmModel realm,
            LogExportConfig legacyConfig, boolean legacyListenerConfigured) {
        SiemConfigRepresentation realmConfig = readRealmConfig(realm);
        RealmModel master = session.realms().getRealmByName(Config.getAdminRealm());
        SiemConfigRepresentation globalConfig = master != null ? readGlobalConfig(master) : null;
        return mergeConfig(realmConfig, globalConfig, toLegacyRepresentation(legacyConfig, legacyListenerConfigured));
    }

    public static SiemEffectiveConfigRepresentation buildEffectiveRepresentation(KeycloakSession session, RealmModel realm,
            LogExportConfig legacyConfig, boolean legacyListenerConfigured) {
        SiemEffectiveConfigRepresentation rep = new SiemEffectiveConfigRepresentation();
        SiemConfigRepresentation realmConfig = readRealmConfig(realm);
        RealmModel master = session.realms().getRealmByName(Config.getAdminRealm());
        SiemConfigRepresentation globalConfig = master != null ? readGlobalConfig(master) : null;
        SiemConfigRepresentation effectiveConfig = mergeConfig(
                realmConfig,
                globalConfig,
                toLegacyRepresentation(legacyConfig, legacyListenerConfigured));

        if (realmConfig != null) {
            rep.setRealmOverride(copy(realmConfig));
        }
        if (globalConfig != null) {
            rep.setGlobalDefaults(copy(globalConfig));
        }
        if (effectiveConfig != null) {
            copyInto(rep, effectiveConfig);
        } else {
            rep.setEnabled(false);
            rep.setInheritGlobal(realmConfig == null || realmConfig.isInheritGlobal());
        }
        return rep;
    }

    public static SiemConfigRepresentation mergeConfig(SiemConfigRepresentation realmConfig,
            SiemConfigRepresentation globalConfig, SiemConfigRepresentation legacyConfig) {
        SiemConfigRepresentation normalizedRealm = normalize(copy(realmConfig));
        if (normalizedRealm != null && !normalizedRealm.isInheritGlobal()) {
            return normalizedRealm;
        }

        SiemConfigRepresentation normalizedGlobal = normalize(copy(globalConfig));
        if (normalizedGlobal != null) {
            return normalizedGlobal;
        }

        return normalize(copy(legacyConfig));
    }

    public static boolean shouldSyncListener(SiemConfigRepresentation effectiveConfig) {
        return effectiveConfig != null
                && effectiveConfig.isEnabled()
                && effectiveConfig.getExportUrl() != null
                && !effectiveConfig.getExportUrl().isBlank();
    }

    public static boolean hasListenerConfigured(RealmModel realm) {
        return realm.getEventsListenersStream().anyMatch(LISTENER_ID::equals);
    }

    public static SiemConfigRepresentation toLegacyRepresentation(LogExportConfig legacyConfig,
            boolean legacyListenerConfigured) {
        if (legacyConfig == null || !legacyListenerConfigured || !legacyConfig.hasExportUrl()) {
            return null;
        }

        SiemConfigRepresentation legacy = new SiemConfigRepresentation();
        legacy.setEnabled(true);
        legacy.setInheritGlobal(true);
        legacy.setExportUrl(legacyConfig.getExportUrl());
        legacy.setAuthToken(legacyConfig.getAuthToken());
        legacy.setSigningSecret(legacyConfig.getSigningSecret());
        legacy.setFormat(legacyConfig.getFormat());
        legacy.setIncludeEvents(new HashSet<>(legacyConfig.getIncludeEvents()));
        legacy.setExcludeEvents(new HashSet<>(legacyConfig.getExcludeEvents()));
        legacy.setIncludeAdminEvents(legacyConfig.isIncludeAdminEvents());
        legacy.setCustomHeaders(new HashMap<>(legacyConfig.getCustomHeaders()));
        return legacy;
    }

    static SiemConfigRepresentation normalize(SiemConfigRepresentation config) {
        if (config == null) {
            return null;
        }
        if (config.getIncludeEvents() == null) {
            config.setIncludeEvents(new HashSet<>());
        }
        if (config.getExcludeEvents() == null) {
            config.setExcludeEvents(new HashSet<>());
        }
        if (config.getCustomHeaders() == null) {
            config.setCustomHeaders(new HashMap<>());
        }
        if (config.getFormat() == null || config.getFormat().isBlank()) {
            config.setFormat(LogExportConfig.FORMAT_JSON);
        }
        return config;
    }

    private static RealmModel resolveRealm(KeycloakSession session, String realmId) {
        if (realmId == null || realmId.isBlank()) {
            return null;
        }
        RealmModel realm = session.realms().getRealm(realmId);
        if (realm != null) {
            return realm;
        }
        return session.realms().getRealmByName(realmId);
    }

    private static SiemConfigRepresentation read(RealmModel realm, String attr) {
        String raw = realm.getAttribute(attr);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JsonSerialization.readValue(raw, SiemConfigRepresentation.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(RealmModel realm, String attr, SiemConfigRepresentation config) {
        try {
            if (config == null) {
                realm.removeAttribute(attr);
            } else {
                realm.setAttribute(attr, JsonSerialization.writeValueAsString(config));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist SIEM config", e);
        }
    }

    private static SiemConfigRepresentation copy(SiemConfigRepresentation config) {
        if (config == null) {
            return null;
        }
        SiemConfigRepresentation copy = new SiemConfigRepresentation();
        copyInto(copy, config);
        return copy;
    }

    private static void copyInto(SiemConfigRepresentation target, SiemConfigRepresentation source) {
        target.setEnabled(source.isEnabled());
        target.setInheritGlobal(source.isInheritGlobal());
        target.setExportUrl(source.getExportUrl());
        target.setAuthToken(source.getAuthToken());
        target.setSigningSecret(source.getSigningSecret());
        target.setFormat(source.getFormat());
        target.setIncludeEvents(new HashSet<>(source.getIncludeEvents()));
        target.setExcludeEvents(new HashSet<>(source.getExcludeEvents()));
        target.setIncludeAdminEvents(source.isIncludeAdminEvents());
        target.setCustomHeaders(new HashMap<>(source.getCustomHeaders()));
    }
}
