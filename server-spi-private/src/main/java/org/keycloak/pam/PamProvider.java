package org.keycloak.pam;

import org.keycloak.provider.Provider;

public interface PamProvider extends Provider {

    PamCheckResult checkAccess(String userId, String username, String clientId, String realmId, String ipAddress);

    void notifySession(String event, String sessionId, String userId, String username, String clientId, String realmId, String ipAddress);
}
