package org.keycloak.services.resources.admin.cloudapps;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.managers.ClientManager;
import org.keycloak.services.resources.KeycloakOpenAPI;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = KeycloakOpenAPI.Admin.Tags.REALMS_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CloudAppsResource {

    /** Name of the built-in client profile that enforces cloud-app security best-practices. */
    public static final String CLOUD_APP_PROFILE = "cloud-application";

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;

    public CloudAppsResource(KeycloakSession session, RealmModel realm,
            AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.adminEvent = adminEvent;
    }

    /**
     * List all cloud application clients for this realm.
     * A client is considered a "cloud application" when it has service accounts enabled
     * (machine-to-machine / client credentials grant).
     */
    @GET
    @Operation(summary = "List cloud application clients for this realm")
    public List<CloudAppRepresentation> list() {
        auth.clients().requireList();
        return session.clients().getClientsStream(realm)
                .filter(ClientModel::isServiceAccountsEnabled)
                .map(this::toRepresentation)
                .collect(Collectors.toList());
    }

    /**
     * Register a new cloud application client.
     * Automatically sets: protocol=openid-connect, serviceAccountsEnabled=true,
     * publicClient=false, standardFlowEnabled=false, implicitFlowEnabled=false,
     * directAccessGrantsEnabled=false, and applies the cloud-application client profile.
     */
    @POST
    @Operation(summary = "Register a new cloud application client")
    public Response register(CloudAppRegistrationRequest request) {
        auth.clients().requireManage();
        if (request == null || request.getClientId() == null || request.getClientId().isBlank()) {
            throw new BadRequestException("clientId is required");
        }
        if (session.clients().getClientByClientId(realm, request.getClientId()) != null) {
            throw new BadRequestException("Client with clientId '" + request.getClientId() + "' already exists");
        }

        ClientRepresentation rep = new ClientRepresentation();
        rep.setClientId(request.getClientId());
        rep.setName(request.getName() != null ? request.getName() : request.getClientId());
        rep.setDescription(request.getDescription());
        rep.setProtocol("openid-connect");
        rep.setServiceAccountsEnabled(true);
        rep.setPublicClient(false);
        rep.setStandardFlowEnabled(false);
        rep.setImplicitFlowEnabled(false);
        rep.setDirectAccessGrantsEnabled(false);
        rep.setFullScopeAllowed(false);

        ClientModel client = ClientManager.createClient(session, realm, rep);

        adminEvent.operation(org.keycloak.events.admin.OperationType.CREATE)
                .resource(org.keycloak.events.admin.ResourceType.CLIENT)
                .resourcePath(client.getId())
                .representation(rep)
                .success();

        return Response.created(
                session.getContext().getUri().getAbsolutePathBuilder()
                        .path(client.getId())
                        .build())
                .entity(toRepresentation(client))
                .build();
    }

    @GET
    @Path("profile")
    @Operation(summary = "Get the name of the built-in cloud-application client profile")
    public Map<String, String> getProfile() {
        auth.clients().requireList();
        return Map.of("profile", CLOUD_APP_PROFILE);
    }

    private CloudAppRepresentation toRepresentation(ClientModel client) {
        CloudAppRepresentation rep = new CloudAppRepresentation();
        rep.setId(client.getId());
        rep.setClientId(client.getClientId());
        rep.setName(client.getName());
        rep.setDescription(client.getDescription());
        rep.setEnabled(client.isEnabled());
        return rep;
    }
}
