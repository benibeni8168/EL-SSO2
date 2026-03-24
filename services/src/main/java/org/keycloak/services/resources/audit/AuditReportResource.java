package org.keycloak.services.resources.audit;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.keycloak.events.Event;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.AdminAuth;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;
import org.keycloak.services.resources.admin.fgap.AdminPermissions;
import org.keycloak.services.resources.audit.AuditReportRepresentation.AdminEventEntry;
import org.keycloak.services.resources.audit.AuditReportRepresentation.EventEntry;
import org.keycloak.services.resources.audit.AuditReportRepresentation.Summary;

import org.jboss.logging.Logger;

/**
 * REST endpoint exposing audit and compliance reports.
 *
 * Accessible at: GET /realms/{realm}/audit-report
 *
 * Query parameters:
 *   fromTime  - epoch millis, start of window (default: 24h ago)
 *   toTime    - epoch millis, end of window (default: now)
 *   maxEvents - max user events to include (default: 500)
 *   maxAdmin  - max admin events to include (default: 500)
 *   type      - report type label for documentation (default: "general")
 */
public class AuditReportResource {

    private static final Logger logger = Logger.getLogger(AuditReportResource.class);

    private static final int DEFAULT_MAX = 500;
    private static final long ONE_DAY_MS = 86_400_000L;

    private final KeycloakSession session;
    private final RealmModel realm;

    public AuditReportResource(KeycloakSession session) {
        this.session = session;
        this.realm = session.getContext().getRealm();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AuditReportRepresentation generateReport(
            @QueryParam("fromTime") Long fromTime,
            @QueryParam("toTime") Long toTime,
            @QueryParam("maxEvents") Integer maxEvents,
            @QueryParam("maxAdmin") Integer maxAdmin,
            @QueryParam("type") String type) {

        AdminAuth adminAuth = AdminRoot.authenticateRealmAdminRequest(session);
        AdminPermissionEvaluator auth = AdminPermissions.evaluator(session, realm, adminAuth);
        auth.realm().requireViewEvents();

        long now = System.currentTimeMillis();
        long from = fromTime != null ? fromTime : now - ONE_DAY_MS;
        long to = toTime != null ? toTime : now;
        int evMax = (maxEvents != null && maxEvents > 0) ? Math.min(maxEvents, 1000) : DEFAULT_MAX;
        int adMax = (maxAdmin != null && maxAdmin > 0) ? Math.min(maxAdmin, 1000) : DEFAULT_MAX;
        String reportType = (type != null && !type.isBlank()) ? type : "general";

        logger.debugf("Audit report for realm=%s from=%d to=%d type=%s", realm.getId(), from, to, reportType);

        EventStoreProvider store = session.getProvider(EventStoreProvider.class);

        AuditReportRepresentation report = new AuditReportRepresentation();
        report.setRealmId(realm.getId());
        report.setRealmName(realm.getName());
        report.setGeneratedAt(now);
        report.setReportType(reportType);

        if (store != null) {
            List<EventEntry> eventEntries = store.createQuery()
                    .realm(realm.getId())
                    .fromDate(from)
                    .toDate(to)
                    .orderByDescTime()
                    .maxResults(evMax)
                    .getResultStream()
                    .map(AuditReportResource::toEventEntry)
                    .collect(Collectors.toList());

            List<AdminEventEntry> adminEntries = store.createAdminQuery()
                    .realm(realm.getId())
                    .fromTime(from)
                    .toTime(to)
                    .orderByDescTime()
                    .maxResults(adMax)
                    .getResultStream()
                    .map(AuditReportResource::toAdminEntry)
                    .collect(Collectors.toList());

            Summary summary = new Summary();
            summary.setTotalEvents(eventEntries.size());
            summary.setTotalAdminEvents(adminEntries.size());
            summary.setFailedLogins(eventEntries.stream()
                    .filter(e -> EventType.LOGIN_ERROR.name().equals(e.getType()))
                    .count());
            Set<String> uniqueUsers = eventEntries.stream()
                    .filter(e -> e.getUserId() != null)
                    .map(EventEntry::getUserId)
                    .collect(Collectors.toSet());
            summary.setUniqueUsers(uniqueUsers.size());

            report.setSummary(summary);
            report.setEvents(eventEntries);
            report.setAdminEvents(adminEntries);
        } else {
            Summary summary = new Summary();
            report.setSummary(summary);
        }

        return report;
    }

    private static EventEntry toEventEntry(Event e) {
        EventEntry entry = new EventEntry();
        entry.setId(e.getId());
        entry.setTime(e.getTime());
        entry.setType(e.getType() != null ? e.getType().name() : null);
        entry.setUserId(e.getUserId());
        entry.setClientId(e.getClientId());
        entry.setIpAddress(e.getIpAddress());
        entry.setError(e.getError());
        entry.setDetails(e.getDetails());
        return entry;
    }

    private static AdminEventEntry toAdminEntry(AdminEvent e) {
        AdminEventEntry entry = new AdminEventEntry();
        entry.setId(e.getId());
        entry.setTime(e.getTime());
        entry.setOperationType(e.getOperationType() != null ? e.getOperationType().name() : null);
        entry.setResourceType(e.getResourceTypeAsString());
        entry.setResourcePath(e.getResourcePath());
        entry.setError(e.getError());
        if (e.getAuthDetails() != null) {
            entry.setAuthUserId(e.getAuthDetails().getUserId());
            entry.setAuthIpAddress(e.getAuthDetails().getIpAddress());
        }
        return entry;
    }
}
