package org.keycloak.events.pam;

import java.util.ArrayList;
import java.util.List;

public class PamConfigRepresentation {

    private boolean enabled;
    private String webhookUrl;
    private String apiToken;
    private List<String> privilegedClients = new ArrayList<>();
    private boolean allClientsPrivileged;
    private boolean notifySessions;
    private int checkTimeoutSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public List<String> getPrivilegedClients() {
        return privilegedClients;
    }

    public void setPrivilegedClients(List<String> privilegedClients) {
        this.privilegedClients = privilegedClients != null ? new ArrayList<>(privilegedClients) : new ArrayList<>();
    }

    public boolean isAllClientsPrivileged() {
        return allClientsPrivileged;
    }

    public void setAllClientsPrivileged(boolean allClientsPrivileged) {
        this.allClientsPrivileged = allClientsPrivileged;
    }

    public boolean isNotifySessions() {
        return notifySessions;
    }

    public void setNotifySessions(boolean notifySessions) {
        this.notifySessions = notifySessions;
    }

    public int getCheckTimeoutSeconds() {
        return checkTimeoutSeconds;
    }

    public void setCheckTimeoutSeconds(int checkTimeoutSeconds) {
        this.checkTimeoutSeconds = checkTimeoutSeconds;
    }
}
