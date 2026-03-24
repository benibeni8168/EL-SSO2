package org.keycloak.services.resources.audit;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured audit/compliance report returned by the audit report endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditReportRepresentation {

    private String realmId;
    private String realmName;
    private long generatedAt;
    private String reportType;
    private Summary summary;
    private List<EventEntry> events;
    private List<AdminEventEntry> adminEvents;

    public static class Summary {
        private long totalEvents;
        private long totalAdminEvents;
        private long failedLogins;
        private long uniqueUsers;

        public long getTotalEvents() { return totalEvents; }
        public void setTotalEvents(long v) { totalEvents = v; }
        public long getTotalAdminEvents() { return totalAdminEvents; }
        public void setTotalAdminEvents(long v) { totalAdminEvents = v; }
        public long getFailedLogins() { return failedLogins; }
        public void setFailedLogins(long v) { failedLogins = v; }
        public long getUniqueUsers() { return uniqueUsers; }
        public void setUniqueUsers(long v) { uniqueUsers = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventEntry {
        private String id;
        private long time;
        private String type;
        private String userId;
        private String clientId;
        private String ipAddress;
        private String error;
        private Map<String, String> details;

        public String getId() { return id; }
        public void setId(String v) { id = v; }
        public long getTime() { return time; }
        public void setTime(long v) { time = v; }
        public String getType() { return type; }
        public void setType(String v) { type = v; }
        public String getUserId() { return userId; }
        public void setUserId(String v) { userId = v; }
        public String getClientId() { return clientId; }
        public void setClientId(String v) { clientId = v; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String v) { ipAddress = v; }
        public String getError() { return error; }
        public void setError(String v) { error = v; }
        public Map<String, String> getDetails() { return details; }
        public void setDetails(Map<String, String> v) { details = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdminEventEntry {
        private String id;
        private long time;
        private String operationType;
        private String resourceType;
        private String resourcePath;
        private String authUserId;
        private String authIpAddress;
        private String error;

        public String getId() { return id; }
        public void setId(String v) { id = v; }
        public long getTime() { return time; }
        public void setTime(long v) { time = v; }
        public String getOperationType() { return operationType; }
        public void setOperationType(String v) { operationType = v; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String v) { resourceType = v; }
        public String getResourcePath() { return resourcePath; }
        public void setResourcePath(String v) { resourcePath = v; }
        public String getAuthUserId() { return authUserId; }
        public void setAuthUserId(String v) { authUserId = v; }
        public String getAuthIpAddress() { return authIpAddress; }
        public void setAuthIpAddress(String v) { authIpAddress = v; }
        public String getError() { return error; }
        public void setError(String v) { error = v; }
    }

    // Root getters/setters
    public String getRealmId() { return realmId; }
    public void setRealmId(String v) { realmId = v; }
    public String getRealmName() { return realmName; }
    public void setRealmName(String v) { realmName = v; }
    public long getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(long v) { generatedAt = v; }
    public String getReportType() { return reportType; }
    public void setReportType(String v) { reportType = v; }
    public Summary getSummary() { return summary; }
    public void setSummary(Summary v) { summary = v; }
    public List<EventEntry> getEvents() { return events; }
    public void setEvents(List<EventEntry> v) { events = v; }
    public List<AdminEventEntry> getAdminEvents() { return adminEvents; }
    public void setAdminEvents(List<AdminEventEntry> v) { adminEvents = v; }
}
