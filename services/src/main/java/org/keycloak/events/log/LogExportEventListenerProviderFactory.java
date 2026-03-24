package org.keycloak.events.log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

public class LogExportEventListenerProviderFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "log-export";
    private static volatile LogExportConfig legacyConfig;

    private LogExportConfig exportConfig;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new LogExportEventListenerProvider(session, exportConfig);
    }

    @Override
    public boolean isGlobal() {
        // Required so master-realm defaults can reach all realms.
        // The provider itself keeps legacy static config behavior scoped to realms that selected the listener.
        return true;
    }

    @Override
    public void init(Config.Scope config) {
        // Maintain backward compatibility with existing config names
        String exportUrl = config.get("export-url", config.get("exportUrl", ""));
        String authToken = config.get("export-auth-token", config.get("exportAuthToken", ""));
        String format = config.get("format", LogExportConfig.FORMAT_JSON);
        
        Set<String> includeEvents = parseCommaSeparatedSet(config.get("include-events", ""));
        Set<String> excludeEvents = parseCommaSeparatedSet(config.get("exclude-events", ""));
        boolean includeAdminEvents = config.getBoolean("include-admin-events", true);
        Map<String, String> customHeaders = parseCustomHeaders(config.get("custom-headers", ""));

        this.exportConfig = new LogExportConfig(
                exportUrl,
                authToken,
                format,
                includeEvents,
                excludeEvents,
                includeAdminEvents,
                customHeaders
        );
        legacyConfig = exportConfig;
    }

    private Set<String> parseCommaSeparatedSet(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Map<String, String> parseCustomHeaders(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new HashMap<>();
        for (String pair : value.split(",")) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex > 0 && colonIndex < pair.length() - 1) {
                String headerName = pair.substring(0, colonIndex).trim();
                String headerValue = pair.substring(colonIndex + 1).trim();
                if (!headerName.isEmpty() && !headerValue.isEmpty() 
                        && isValidHeaderName(headerName) && isValidHeaderValue(headerValue)) {
                    headers.put(headerName, headerValue);
                }
            }
        }
        return headers;
    }

    /**
     * Validates HTTP header name per RFC 7230.
     * Header names must be tokens (no control chars, separators, or whitespace).
     */
    private boolean isValidHeaderName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            // Reject control characters (0-31, 127) and common separators
            if (c <= 32 || c >= 127 || c == ':' || c == '\r' || c == '\n') {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates HTTP header value - rejects CRLF injection attempts.
     */
    private boolean isValidHeaderValue(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // Reject carriage return and line feed to prevent header injection
            if (c == '\r' || c == '\n') {
                return false;
            }
        }
        return true;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    public LogExportConfig getConfiguredStaticConfig() {
        return exportConfig;
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name("export-url")
                .type("string")
                .helpText("URL to export events to. Events will be sent as HTTP POST requests.")
                .add()
                .property()
                .name("export-auth-token")
                .type("string")
                .helpText("Authorization header value for the export request (e.g., 'Bearer <token>').")
                .secret(true)
                .add()
                .property()
                .name("format")
                .type("string")
                .helpText("Output format for exported events: 'json' (default), 'syslog' (RFC 5424), or 'cef' (Common Event Format for SIEM).")
                .defaultValue(LogExportConfig.FORMAT_JSON)
                .add()
                .property()
                .name("include-events")
                .type("string")
                .helpText("Comma-separated list of event types to include. If empty, all events are included (unless excluded).")
                .add()
                .property()
                .name("exclude-events")
                .type("string")
                .helpText("Comma-separated list of event types to exclude from export.")
                .add()
                .property()
                .name("include-admin-events")
                .type("boolean")
                .helpText("Whether to export admin events. Default is true.")
                .defaultValue("true")
                .add()
                .property()
                .name("custom-headers")
                .type("string")
                .helpText("Additional HTTP headers in format 'Header1:Value1,Header2:Value2'.")
                .add()
                .build();
    }

    public static LogExportConfig getLegacyConfig() {
        return legacyConfig;
    }
}
