package org.keycloak.services.resources.audit;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class AuditReportResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public AuditReportResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new AuditReportResource(session);
    }

    @Override
    public void close() {
        // no-op
    }
}
