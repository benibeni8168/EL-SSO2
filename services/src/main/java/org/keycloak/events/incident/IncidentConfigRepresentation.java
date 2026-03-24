package org.keycloak.events.incident;

import java.util.ArrayList;
import java.util.List;

public class IncidentConfigRepresentation {

    private boolean enabled;
    private String webhookUrl;
    private String apiToken;
    private List<String> triggerEventTypes = new ArrayList<>();
    private boolean includeAdminEvents;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public List<String> getTriggerEventTypes() { return triggerEventTypes; }
    public void setTriggerEventTypes(List<String> triggerEventTypes) {
        this.triggerEventTypes = triggerEventTypes != null ? new ArrayList<>(triggerEventTypes) : new ArrayList<>();
    }

    public boolean isIncludeAdminEvents() { return includeAdminEvents; }
    public void setIncludeAdminEvents(boolean includeAdminEvents) { this.includeAdminEvents = includeAdminEvents; }
}
