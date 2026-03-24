package org.keycloak.services.resources.admin.pam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.events.pam.PamConfigRepresentation;
import org.keycloak.events.pam.PamConfigStore;
import org.keycloak.events.pam.PamSessionEventListenerProviderFactory;
import org.keycloak.events.pam.PamTestResultRepresentation;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.KeycloakOpenAPI;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Tag(name = KeycloakOpenAPI.Admin.Tags.REALMS_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PamResource {

    private static final Logger logger = Logger.getLogger(PamResource.class);

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;

    public PamResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.adminEvent = adminEvent;
    }

    @GET
    @Operation(summary = "Get PAM integration config for the current realm")
    public PamConfigRepresentation get() {
        auth.realm().requireViewEvents();
        PamConfigRepresentation config = PamConfigStore.readConfig(realm);
        return config != null ? config : new PamConfigRepresentation();
    }

    @PUT
    @Operation(summary = "Update PAM integration config for the current realm")
    public Response update(PamConfigRepresentation rep) {
        auth.realm().requireManageEvents();
        if (rep == null) {
            throw new BadRequestException("Missing PAM config");
        }

        PamConfigStore.writeConfig(realm, rep);
        syncRealmEventListeners(realm, rep);

        adminEvent.operation(org.keycloak.events.admin.OperationType.UPDATE)
                .resource(org.keycloak.events.admin.ResourceType.REALM)
                .representation(rep)
                .success();
        return Response.noContent().build();
    }

    @POST
    @Path("test")
    @Operation(summary = "Send a PAM test request")
    public PamTestResultRepresentation test() {
        auth.realm().requireManageEvents();
        PamConfigRepresentation config = PamConfigStore.readConfig(realm);

        if (config == null || !config.isEnabled() || config.getWebhookUrl() == null
                || config.getWebhookUrl().isBlank()) {
            throw new BadRequestException("PAM is not enabled or webhook URL is not configured");
        }

        PamTestResultRepresentation result = new PamTestResultRepresentation();
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            String url = config.getWebhookUrl() + "/test";
            String payload = "{\"test\":true}";

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (config.getApiToken() != null && !config.getApiToken().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiToken());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            result.setSent(true);
            result.setStatus(String.valueOf(response.statusCode()));
            result.setMessage(response.statusCode() < 400
                    ? "PAM test request sent successfully"
                    : "PAM test request returned status " + response.statusCode());

        } catch (Exception e) {
            logger.warnf("PAM test failed: %s", e.getMessage());
            result.setSent(false);
            result.setStatus("error");
            result.setMessage("PAM test request failed: " + e.getMessage());
        }

        return result;
    }

    private void syncRealmEventListeners(RealmModel currentRealm, PamConfigRepresentation config) {
        Set<String> listeners = new HashSet<>(currentRealm.getEventsListenersStream().toList());
        String listenerId = PamSessionEventListenerProviderFactory.PROVIDER_ID;
        if (config.isEnabled() && config.isNotifySessions()) {
            listeners.add(listenerId);
        } else {
            listeners.remove(listenerId);
        }
        currentRealm.setEventsListeners(listeners);
    }
}
