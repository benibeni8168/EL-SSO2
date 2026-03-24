package org.keycloak.events.log;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LogExportPayloadBuilder {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String KEYCLOAK_VENDOR = "Keycloak";
    private static final String KEYCLOAK_PRODUCT = "IAM";
    private static final String KEYCLOAK_VERSION = "1.0";

    // Syslog facility (1 = user-level) and severity (6 = informational) -> priority = 1*8+6 = 14
    private static final int SYSLOG_PRIORITY = 14;
    private static final int SYSLOG_VERSION = 1;

    private LogExportPayloadBuilder() {}

    /**
     * Builds payload in the specified format.
     * 
     * @param event the event to serialize
     * @param format the output format: "json", "syslog", or "cef"
     * @return formatted payload string
     */
    public static String buildPayload(Event event, String format) {
        if (format == null) {
            format = LogExportConfig.FORMAT_JSON;
        }
        switch (format.toLowerCase()) {
            case LogExportConfig.FORMAT_SYSLOG:
                return buildSyslogPayload(event);
            case LogExportConfig.FORMAT_CEF:
                return buildCefPayload(event);
            case LogExportConfig.FORMAT_JSON:
            default:
                return buildPayload(event);
        }
    }

    /**
     * Builds payload in the specified format for admin events.
     * 
     * @param event the admin event to serialize
     * @param format the output format: "json", "syslog", or "cef"
     * @return formatted payload string
     */
    public static String buildPayload(AdminEvent event, String format) {
        if (format == null) {
            format = LogExportConfig.FORMAT_JSON;
        }
        switch (format.toLowerCase()) {
            case LogExportConfig.FORMAT_SYSLOG:
                return buildSyslogPayload(event);
            case LogExportConfig.FORMAT_CEF:
                return buildCefPayload(event);
            case LogExportConfig.FORMAT_JSON:
            default:
                return buildPayload(event);
        }
    }

    public static String buildPayload(Event event) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "USER_EVENT");
        node.put("eventType", event.getType() != null ? event.getType().name() : null);
        node.put("realmId", event.getRealmId());
        node.put("clientId", event.getClientId());
        node.put("userId", event.getUserId());
        node.put("ipAddress", event.getIpAddress());
        node.put("time", event.getTime());
        if (event.getError() != null) {
            node.put("error", event.getError());
        }
        if (event.getDetails() != null) {
            ObjectNode details = mapper.valueToTree(event.getDetails());
            node.set("details", details);
        }
        return toJson(node);
    }

    public static String buildPayload(AdminEvent event) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "ADMIN_EVENT");
        node.put("operationType", event.getOperationType() != null ? event.getOperationType().name() : null);
        node.put("realmId", event.getRealmId());
        node.put("resourceType", event.getResourceType() != null ? event.getResourceType().name() : null);
        node.put("resourcePath", event.getResourcePath());
        node.put("time", event.getTime());
        if (event.getError() != null) {
            node.put("error", event.getError());
        }
        if (event.getAuthDetails() != null) {
            ObjectNode auth = mapper.createObjectNode();
            auth.put("clientId", event.getAuthDetails().getClientId());
            auth.put("userId", event.getAuthDetails().getUserId());
            auth.put("ipAddress", event.getAuthDetails().getIpAddress());
            auth.put("realmId", event.getAuthDetails().getRealmId());
            node.set("authDetails", auth);
        }
        return toJson(node);
    }

    /**
     * Builds a syslog message in RFC 5424 format.
     * Format: <priority>version timestamp hostname app-name procid msgid structured-data msg
     * Example: <14>1 2026-03-18T12:00:00Z keycloak - - - {"eventType":"LOGIN",...}
     */
    public static String buildSyslogPayload(Event event) {
        StringBuilder sb = new StringBuilder();
        
        // Priority and version
        sb.append("<").append(SYSLOG_PRIORITY).append(">").append(SYSLOG_VERSION).append(" ");
        
        // Timestamp in ISO 8601 format
        String timestamp = formatTimestamp(event.getTime());
        sb.append(timestamp).append(" ");
        
        // Hostname (using "keycloak" as default)
        sb.append("keycloak ");
        
        // App-name
        sb.append("keycloak ");
        
        // Procid (NILVALUE)
        sb.append("- ");
        
        // Msgid (event type or NILVALUE)
        String eventType = event.getType() != null ? event.getType().name() : "-";
        sb.append(eventType).append(" ");
        
        // Structured data (NILVALUE for simplicity)
        sb.append("- ");
        
        // Message (JSON payload)
        sb.append(buildPayload(event));
        
        return sb.toString();
    }

    /**
     * Builds a syslog message in RFC 5424 format for admin events.
     */
    public static String buildSyslogPayload(AdminEvent event) {
        StringBuilder sb = new StringBuilder();
        
        // Priority and version
        sb.append("<").append(SYSLOG_PRIORITY).append(">").append(SYSLOG_VERSION).append(" ");
        
        // Timestamp in ISO 8601 format
        String timestamp = formatTimestamp(event.getTime());
        sb.append(timestamp).append(" ");
        
        // Hostname
        sb.append("keycloak ");
        
        // App-name
        sb.append("keycloak ");
        
        // Procid (NILVALUE)
        sb.append("- ");
        
        // Msgid (operation type or NILVALUE)
        String operationType = event.getOperationType() != null ? event.getOperationType().name() : "-";
        sb.append(operationType).append(" ");
        
        // Structured data (NILVALUE)
        sb.append("- ");
        
        // Message (JSON payload)
        sb.append(buildPayload(event));
        
        return sb.toString();
    }

    /**
     * Builds a CEF (Common Event Format) message for SIEM integration.
     * Format: CEF:Version|Device Vendor|Device Product|Device Version|Signature ID|Name|Severity|Extension
     * Example: CEF:0|Keycloak|IAM|1.0|LOGIN|User Login|3|src=192.168.1.1 suser=john rt=1710763200000
     */
    public static String buildCefPayload(Event event) {
        StringBuilder sb = new StringBuilder();
        
        // CEF header
        sb.append("CEF:0|");
        sb.append(KEYCLOAK_VENDOR).append("|");
        sb.append(KEYCLOAK_PRODUCT).append("|");
        sb.append(KEYCLOAK_VERSION).append("|");
        
        // Signature ID (event type)
        String eventType = event.getType() != null ? event.getType().name() : "UNKNOWN";
        sb.append(escapeForCef(eventType)).append("|");
        
        // Name (human-readable event name)
        sb.append(escapeForCef(getEventName(eventType))).append("|");
        
        // Severity (0-10, using 3 for info, 7 for errors)
        int severity = event.getError() != null ? 7 : 3;
        sb.append(severity).append("|");
        
        // Extension fields
        appendCefExtension(sb, "rt", String.valueOf(event.getTime()));
        
        if (event.getIpAddress() != null) {
            appendCefExtension(sb, "src", event.getIpAddress());
        }
        
        if (event.getUserId() != null) {
            appendCefExtension(sb, "suid", event.getUserId());
        }
        
        if (event.getClientId() != null) {
            appendCefExtension(sb, "cs1", event.getClientId());
            appendCefExtension(sb, "cs1Label", "clientId");
        }
        
        if (event.getRealmId() != null) {
            appendCefExtension(sb, "cs2", event.getRealmId());
            appendCefExtension(sb, "cs2Label", "realmId");
        }
        
        if (event.getError() != null) {
            appendCefExtension(sb, "reason", event.getError());
        }
        
        // Add username from details if available
        if (event.getDetails() != null && event.getDetails().containsKey("username")) {
            appendCefExtension(sb, "suser", event.getDetails().get("username"));
        }
        
        return sb.toString().trim();
    }

    /**
     * Builds a CEF message for admin events.
     */
    public static String buildCefPayload(AdminEvent event) {
        StringBuilder sb = new StringBuilder();
        
        // CEF header
        sb.append("CEF:0|");
        sb.append(KEYCLOAK_VENDOR).append("|");
        sb.append(KEYCLOAK_PRODUCT).append("|");
        sb.append(KEYCLOAK_VERSION).append("|");
        
        // Signature ID (operation type)
        String operationType = event.getOperationType() != null ? event.getOperationType().name() : "UNKNOWN";
        sb.append("ADMIN_").append(escapeForCef(operationType)).append("|");
        
        // Name
        sb.append("Admin ").append(escapeForCef(getEventName(operationType))).append("|");
        
        // Severity
        int severity = event.getError() != null ? 7 : 5;
        sb.append(severity).append("|");
        
        // Extension fields
        appendCefExtension(sb, "rt", String.valueOf(event.getTime()));
        
        if (event.getAuthDetails() != null) {
            if (event.getAuthDetails().getIpAddress() != null) {
                appendCefExtension(sb, "src", event.getAuthDetails().getIpAddress());
            }
            if (event.getAuthDetails().getUserId() != null) {
                appendCefExtension(sb, "suid", event.getAuthDetails().getUserId());
            }
            if (event.getAuthDetails().getClientId() != null) {
                appendCefExtension(sb, "cs1", event.getAuthDetails().getClientId());
                appendCefExtension(sb, "cs1Label", "clientId");
            }
            if (event.getAuthDetails().getRealmId() != null) {
                appendCefExtension(sb, "cs2", event.getAuthDetails().getRealmId());
                appendCefExtension(sb, "cs2Label", "realmId");
            }
        }
        
        if (event.getResourceType() != null) {
            appendCefExtension(sb, "cs3", event.getResourceType().name());
            appendCefExtension(sb, "cs3Label", "resourceType");
        }
        
        if (event.getResourcePath() != null) {
            appendCefExtension(sb, "cs4", event.getResourcePath());
            appendCefExtension(sb, "cs4Label", "resourcePath");
        }
        
        if (event.getError() != null) {
            appendCefExtension(sb, "reason", event.getError());
        }
        
        return sb.toString().trim();
    }

    private static void appendCefExtension(StringBuilder sb, String key, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(key).append("=").append(escapeForCefExtension(value)).append(" ");
        }
    }

    /**
     * Escapes special characters for CEF header fields (pipe and backslash).
     */
    private static String escapeForCef(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("|", "\\|");
    }

    /**
     * Escapes special characters for CEF extension values (equals, backslash, newlines).
     */
    private static String escapeForCefExtension(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("=", "\\=")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Converts event type to human-readable name.
     */
    private static String getEventName(String eventType) {
        if (eventType == null) {
            return "Unknown Event";
        }
        // Convert SCREAMING_SNAKE_CASE to Title Case
        String[] parts = eventType.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }

    private static String formatTimestamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
    }

    private static String toJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
