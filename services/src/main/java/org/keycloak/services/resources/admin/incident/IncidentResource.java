package org.keycloak.services.resources.admin.incident;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.events.incident.IncidentConfigRepresentation;
import org.keycloak.events.incident.IncidentConfigStore;
import org.keycloak.events.incident.IncidentEventListenerProviderFactory;
import org.keycloak.events.incident.IncidentTestResultRepresentation;
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
public class IncidentResource {

    private static final Logger logger = Logger.getLogger(IncidentResource.class);

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;

    public IncidentResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.adminEvent = adminEvent;
    }

    @GET
    @Operation(summary = "Get incident integration config for the current realm")
    public IncidentConfigRepresentation get() {
        auth.realm().requireViewEvents();
        IncidentConfigRepresentation config = IncidentConfigStore.readConfig(realm);
        return config != null ? config : new IncidentConfigRepresentation();
    }

    @PUT
    @Operation(summary = "Update incident integration config for the current realm")
    public Response update(IncidentConfigRepresentation rep) {
        auth.realm().requireManageEvents();
        if (rep == null) {
            throw new BadRequestException("Missing incident config");
        }

        IncidentConfigStore.writeConfig(realm, rep);
        syncRealmEventListeners(realm, rep);

        adminEvent.operation(OperationType.UPDATE)
                .resource(ResourceType.REALM)
                .representation(rep)
                .success();
        return Response.noContent().build();
    }

    @POST
    @Path("test")
    @Operation(summary = "Send an incident test request")
    public IncidentTestResultRepresentation test() {
        auth.realm().requireManageEvents();
        IncidentConfigRepresentation config = IncidentConfigStore.readConfig(realm);

        if (config == null || !config.isEnabled() || config.getWebhookUrl() == null
                || config.getWebhookUrl().isBlank()) {
            throw new BadRequestException("Incident integration is not enabled or webhook URL is not configured");
        }

        IncidentTestResultRepresentation result = new IncidentTestResultRepresentation();
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("source", "keycloak");
            payloadNode.put("incidentType", "AUTH_EVENT");
            payloadNode.put("eventType", "LOGIN_ERROR");
            Instant now = Instant.now();
            payloadNode.put("timestamp", now.toString());
            payloadNode.put("timestampEpochMs", now.toEpochMilli());
            payloadNode.put("realmName", realm.getName());
            payloadNode.put("clientId", "test-client");
            payloadNode.put("userId", "incident-test-user");
            payloadNode.put("ipAddress", "127.0.0.1");
            payloadNode.put("error", "test_connection");
            payloadNode.putObject("details");
            String payload = objectMapper.writeValueAsString(payloadNode);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getWebhookUrl()))
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
                    ? "Incident test request sent successfully"
                    : "Incident test request returned status " + response.statusCode());

        } catch (Exception e) {
            logger.warnf("Incident test failed: %s", e.getMessage());
            result.setSent(false);
            result.setStatus("error");
            result.setMessage("Incident test request failed: " + e.getMessage());
        }

        return result;
    }

    private void syncRealmEventListeners(RealmModel currentRealm, IncidentConfigRepresentation config) {
        Set<String> listeners = new HashSet<>(currentRealm.getEventsListenersStream().toList());
        String listenerId = IncidentEventListenerProviderFactory.PROVIDER_ID;
        if (config.isEnabled()) {
            listeners.add(listenerId);
        } else {
            listeners.remove(listenerId);
        }
        currentRealm.setEventsListeners(listeners);
    }
}
