package org.keycloak.events.log;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Configuration holder for log export event listener.
 */
public class LogExportConfig {

    public static final String FORMAT_JSON = "json";
    public static final String FORMAT_SYSLOG = "syslog";
    public static final String FORMAT_CEF = "cef";

    private final String exportUrl;
    private final String authToken;
    private final String format;
    private final Set<String> includeEvents;
    private final Set<String> excludeEvents;
    private final boolean includeAdminEvents;
    private final Map<String, String> customHeaders;
    private final String signingSecret;

    public LogExportConfig(String exportUrl, String authToken, String format,
                           Set<String> includeEvents, Set<String> excludeEvents,
                           boolean includeAdminEvents, Map<String, String> customHeaders) {
        this(exportUrl, authToken, format, includeEvents, excludeEvents, includeAdminEvents, customHeaders, null);
    }

    public LogExportConfig(String exportUrl, String authToken, String format,
                           Set<String> includeEvents, Set<String> excludeEvents,
                           boolean includeAdminEvents, Map<String, String> customHeaders, String signingSecret) {
        this.exportUrl = exportUrl;
        this.authToken = authToken;
        this.format = format != null ? format : FORMAT_JSON;
        this.includeEvents = includeEvents != null ? Collections.unmodifiableSet(includeEvents) : Collections.emptySet();
        this.excludeEvents = excludeEvents != null ? Collections.unmodifiableSet(excludeEvents) : Collections.emptySet();
        this.includeAdminEvents = includeAdminEvents;
        this.customHeaders = customHeaders != null ? Collections.unmodifiableMap(customHeaders) : Collections.emptyMap();
        this.signingSecret = signingSecret;
    }

    public String getExportUrl() {
        return exportUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getFormat() {
        return format;
    }

    public Set<String> getIncludeEvents() {
        return includeEvents;
    }

    public Set<String> getExcludeEvents() {
        return excludeEvents;
    }

    public boolean isIncludeAdminEvents() {
        return includeAdminEvents;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public boolean hasExportUrl() {
        return exportUrl != null && !exportUrl.isEmpty();
    }
}
