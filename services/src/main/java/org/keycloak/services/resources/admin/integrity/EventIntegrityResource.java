package org.keycloak.services.resources.admin.integrity;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.keycloak.events.EventIntegrityResult;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.KeycloakOpenAPI;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = KeycloakOpenAPI.Admin.Tags.REALMS_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
public class EventIntegrityResource {

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;

    public EventIntegrityResource(
            KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
    }

    @GET
    @Operation(summary = "Verify the SHA-256 integrity of stored events for this realm")
    public EventIntegrityRepresentation verify() {
        auth.realm().requireViewEvents();
        EventStoreProvider store = session.getProvider(EventStoreProvider.class);
        EventIntegrityResult result = store.verifyIntegrity(realm);
        return new EventIntegrityRepresentation(
                result.getChecked(),
                result.getValid(),
                result.getTampered(),
                result.getLegacyNoHash());
    }
}
