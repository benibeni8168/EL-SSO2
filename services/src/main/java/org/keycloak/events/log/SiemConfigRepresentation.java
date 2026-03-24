package org.keycloak.events.log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SIEM forwarding configuration persisted in realm attributes.
 */
public class SiemConfigRepresentation {

    private static final String TRANSPORT_HTTP = "http";

    private boolean enabled;
    private boolean inheritGlobal = true;
    private String exportUrl;
    private String authToken;
    private String signingSecret;
    private String format = LogExportConfig.FORMAT_JSON;
    private Set<String> includeEvents = new HashSet<>();
    private Set<String> excludeEvents = new HashSet<>();
    private boolean includeAdminEvents = true;
    private Map<String, String> customHeaders = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTransport() {
        return TRANSPORT_HTTP;
    }

    public void setTransport(String transport) {
        // Phase 1 only supports HTTP transport. Accept and ignore client echo.
    }

    public boolean isInheritGlobal() {
        return inheritGlobal;
    }

    public void setInheritGlobal(boolean inheritGlobal) {
        this.inheritGlobal = inheritGlobal;
    }

    public String getExportUrl() {
        return exportUrl;
    }

    public void setExportUrl(String exportUrl) {
        this.exportUrl = exportUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Set<String> getIncludeEvents() {
        return includeEvents;
    }

    public void setIncludeEvents(Set<String> includeEvents) {
        this.includeEvents = includeEvents != null ? new HashSet<>(includeEvents) : new HashSet<>();
    }

    public Set<String> getExcludeEvents() {
        return excludeEvents;
    }

    public void setExcludeEvents(Set<String> excludeEvents) {
        this.excludeEvents = excludeEvents != null ? new HashSet<>(excludeEvents) : new HashSet<>();
    }

    public boolean isIncludeAdminEvents() {
        return includeAdminEvents;
    }

    public void setIncludeAdminEvents(boolean includeAdminEvents) {
        this.includeAdminEvents = includeAdminEvents;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : new HashMap<>();
    }
}
