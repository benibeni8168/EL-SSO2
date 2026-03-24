package org.keycloak.services.resources.admin.siem;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.log.LogExportConfig;
import org.keycloak.events.log.LogExportEventListenerProvider;
import org.keycloak.events.log.LogExportEventListenerProviderFactory;
import org.keycloak.events.log.SiemConfigRepresentation;
import org.keycloak.events.log.SiemConfigStore;
import org.keycloak.events.log.SiemEffectiveConfigRepresentation;
import org.keycloak.events.log.SiemTestResultRepresentation;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.KeycloakOpenAPI;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = KeycloakOpenAPI.Admin.Tags.REALMS_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SiemResource {

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;

    public SiemResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.adminEvent = adminEvent;
    }

    @GET
    @Operation(summary = "Get SIEM forwarding config for the current realm")
    public SiemEffectiveConfigRepresentation get() {
        auth.realm().requireViewEvents();
        SiemEffectiveConfigRepresentation rep = SiemConfigStore.buildEffectiveRepresentation(
                session,
                realm,
                legacyStaticConfig(),
                SiemConfigStore.hasListenerConfigured(realm));
        maskSigningSecret(rep);
        if (rep.getRealmOverride() != null) {
            maskSigningSecret(rep.getRealmOverride());
        }
        if (rep.getGlobalDefaults() != null) {
            maskSigningSecret(rep.getGlobalDefaults());
        }
        return rep;
    }

    private static final String MASKED_SECRET = "***";

    private void maskSigningSecret(SiemConfigRepresentation rep) {
        if (rep.getSigningSecret() != null && !rep.getSigningSecret().isBlank()) {
            rep.setSigningSecret(MASKED_SECRET);
        }
    }

    private void preserveSigningSecretIfMasked(SiemConfigRepresentation incoming, SiemConfigRepresentation stored) {
        if (MASKED_SECRET.equals(incoming.getSigningSecret())) {
            incoming.setSigningSecret(stored != null ? stored.getSigningSecret() : null);
        }
    }

    @PUT
    @Operation(summary = "Update SIEM forwarding config for the current realm")
    public Response update(SiemConfigRepresentation rep) {
        auth.realm().requireManageEvents();
        if (rep == null) {
            throw new BadRequestException("Missing SIEM config");
        }

        preserveSigningSecretIfMasked(rep, SiemConfigStore.readRealmConfig(realm));
        SiemConfigStore.writeRealmConfig(realm, rep);
        syncRealmEventListeners(realm);

        adminEvent.operation(org.keycloak.events.admin.OperationType.UPDATE)
                .resource(org.keycloak.events.admin.ResourceType.REALM)
                .representation(rep)
                .success();
        return Response.noContent().build();
    }

    @POST
    @Path("test")
    @Operation(summary = "Send a synthetic SIEM test event")
    public SiemTestResultRepresentation test() {
        auth.realm().requireManageEvents();
        SiemConfigRepresentation effective = SiemConfigStore.resolveEffectiveConfig(
                session,
                realm,
                legacyStaticConfig(),
                SiemConfigStore.hasListenerConfigured(realm));
        if (effective == null || !effective.isEnabled() || effective.getExportUrl() == null
                || effective.getExportUrl().isBlank()) {
            throw new BadRequestException("SIEM forwarding is disabled");
        }

        LogExportConfig config = new LogExportConfig(
                effective.getExportUrl(),
                effective.getAuthToken(),
                effective.getFormat(),
                effective.getIncludeEvents(),
                effective.getExcludeEvents(),
                effective.isIncludeAdminEvents(),
                effective.getCustomHeaders(),
                effective.getSigningSecret());
        LogExportEventListenerProvider provider = new LogExportEventListenerProvider(session, config);
        provider.sendTestEvent(config, realm);

        SiemTestResultRepresentation result = new SiemTestResultRepresentation();
        result.setSent(true);
        result.setStatus("queued");
        result.setMessage("Synthetic login event dispatched");
        return result;
    }

    @GET
    @Path("global")
    @Operation(summary = "Get global SIEM defaults")
    public SiemConfigRepresentation getGlobal() {
        requireMasterView();
        SiemConfigRepresentation config = SiemConfigStore.readGlobalConfig(realm);
        if (config == null) {
            return new SiemConfigRepresentation();
        }
        maskSigningSecret(config);
        return config;
    }

    @PUT
    @Path("global")
    @Operation(summary = "Update global SIEM defaults")
    public Response updateGlobal(SiemConfigRepresentation rep) {
        requireMaster();
        if (rep == null) {
            throw new BadRequestException("Missing SIEM config");
        }

        preserveSigningSecretIfMasked(rep, SiemConfigStore.readGlobalConfig(realm));
        SiemConfigStore.writeGlobalConfig(realm, rep);
        syncAllRealmEventListeners();

        adminEvent.operation(org.keycloak.events.admin.OperationType.UPDATE)
                .resource(org.keycloak.events.admin.ResourceType.REALM)
                .representation(rep)
                .success();
        return Response.noContent().build();
    }

    private void syncRealmEventListeners(RealmModel currentRealm) {
        SiemConfigRepresentation effective = SiemConfigStore.resolveEffectiveConfig(
                session,
                currentRealm,
                legacyStaticConfig(),
                SiemConfigStore.hasListenerConfigured(currentRealm));
        Set<String> listeners = new HashSet<>(currentRealm.getEventsListenersStream().toList());
        if (SiemConfigStore.shouldSyncListener(effective)) {
            listeners.add(SiemConfigStore.LISTENER_ID);
        } else {
            listeners.remove(SiemConfigStore.LISTENER_ID);
        }
        currentRealm.setEventsListeners(listeners);
    }

    private void syncAllRealmEventListeners() {
        session.realms().getRealmsStream().forEach(this::syncRealmEventListeners);
    }

    private void requireMasterView() {
        auth.realm().requireViewEvents();
        if (!Config.getAdminRealm().equals(realm.getName())) {
            throw new ForbiddenException("Global SIEM settings are only available in the master realm");
        }
    }

    private void requireMaster() {
        auth.realm().requireManageEvents();
        if (!Config.getAdminRealm().equals(realm.getName())) {
            throw new ForbiddenException("Global SIEM settings are only available in the master realm");
        }
    }

    private LogExportConfig legacyStaticConfig() {
        LogExportEventListenerProviderFactory factory =
                (LogExportEventListenerProviderFactory) session.getKeycloakSessionFactory()
                        .getProviderFactory(EventListenerProvider.class, LogExportEventListenerProviderFactory.PROVIDER_ID);
        return factory != null ? factory.getConfiguredStaticConfig() : null;
    }
}
