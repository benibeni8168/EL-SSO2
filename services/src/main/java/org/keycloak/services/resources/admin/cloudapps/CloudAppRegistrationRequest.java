package org.keycloak.services.resources.admin.cloudapps;

/**
 * Request body for registering a new cloud application client.
 */
public class CloudAppRegistrationRequest {

    private String clientId;
    private String name;
    private String description;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
