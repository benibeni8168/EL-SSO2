/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.services.resources.admin.audit;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.keycloak.events.Event;
import org.keycloak.events.EventQuery;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;
import org.keycloak.services.util.DateUtil;
import org.keycloak.util.JsonSerialization;

/**
 * Resource for per-user activity event reports (GDPR data subject access).
 */
public class UserActivityReportResource {

    private static final int MAX_EXPORT_RESULTS = 10000;
    private static final int MAX_SUMMARY_RESULTS = 100000;

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;

    public UserActivityReportResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
    }

    /**
     * Get all events summary for a specific user.
     *
     * @param userId  The user ID to report on (required)
     * @param dateFrom From date (yyyy-MM-dd or epoch millis)
     * @param dateTo   To date (yyyy-MM-dd or epoch millis)
     * @return Summary of events for the user
     */
    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    public ReportSummary getSummary(
            @QueryParam("userId") String userId,
            @QueryParam("dateFrom") String dateFrom,
            @QueryParam("dateTo") String dateTo) {
        auth.realm().requireViewEvents();

        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("userId query parameter is required");
        }

        EventStoreProvider eventStore = session.getProvider(EventStoreProvider.class);
        EventQuery query = eventStore.createQuery().realm(realm.getId()).user(userId);

        applyDateFilters(query, dateFrom, dateTo);
        query.maxResults(MAX_SUMMARY_RESULTS);

        List<Event> events = query.getResultStream().toList();

        return buildSummary(events, dateFrom, dateTo);
    }

    /**
     * Export all events for a specific user.
     *
     * @param userId  The user ID to export events for (required)
     * @param format  Export format (json or csv)
     * @param dateFrom From date (yyyy-MM-dd or epoch millis)
     * @param dateTo   To date (yyyy-MM-dd or epoch millis)
     * @return Exported events in the requested format
     */
    @GET
    @Path("/export")
    @Produces({MediaType.APPLICATION_JSON, "text/csv"})
    public Response export(
            @QueryParam("userId") String userId,
            @QueryParam("format") @DefaultValue("json") String format,
            @QueryParam("dateFrom") String dateFrom,
            @QueryParam("dateTo") String dateTo) {
        auth.realm().requireViewEvents();

        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("userId query parameter is required");
        }

        EventStoreProvider eventStore = session.getProvider(EventStoreProvider.class);
        EventQuery query = eventStore.createQuery().realm(realm.getId()).user(userId);

        applyDateFilters(query, dateFrom, dateTo);
        query.maxResults(MAX_EXPORT_RESULTS);

        List<Event> events = query.getResultStream().toList();

        if ("csv".equalsIgnoreCase(format)) {
            StreamingOutput stream = output -> CsvExporter.exportEvents(events, output);
            return Response.ok(stream)
                    .type("text/csv")
                    .header("Content-Disposition", "attachment; filename=\"user-activity-events.csv\"")
                    .build();
        } else {
            StreamingOutput stream = output -> {
                try {
                    JsonSerialization.writeValueToStream(output, events);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to serialize events to JSON", e);
                }
            };
            return Response.ok(stream)
                    .type(MediaType.APPLICATION_JSON)
                    .header("Content-Disposition", "attachment; filename=\"user-activity-events.json\"")
                    .build();
        }
    }

    private void applyDateFilters(EventQuery query, String dateFrom, String dateTo) {
        if (dateFrom != null) {
            try {
                query.fromDate(DateUtil.toStartOfDay(dateFrom));
            } catch (Throwable t) {
                throw new BadRequestException("Invalid value for 'dateFrom', expected format is yyyy-MM-dd or an Epoch timestamp");
            }
        }

        if (dateTo != null) {
            try {
                query.toDate(DateUtil.toEndOfDay(dateTo));
            } catch (Throwable t) {
                throw new BadRequestException("Invalid value for 'dateTo', expected format is yyyy-MM-dd or an Epoch timestamp");
            }
        }
    }

    private ReportSummary buildSummary(List<Event> events, String dateFrom, String dateTo) {
        long successCount = 0;
        long failureCount = 0;
        Map<String, Long> countsByType = new HashMap<>();

        for (Event event : events) {
            String typeName = event.getType().name();
            countsByType.merge(typeName, 1L, Long::sum);

            if (event.getError() != null || typeName.endsWith("_ERROR")) {
                failureCount++;
            } else {
                successCount++;
            }
        }

        ReportSummary summary = new ReportSummary(
                events.size(),
                successCount,
                failureCount,
                countsByType,
                dateFrom,
                dateTo
        );
        summary.setGeneratedAt(java.time.Instant.now().toString());
        summary.setGeneratedByUserId(auth.adminAuth().getUser().getId());
        summary.setRealmName(realm.getName());
        summary.setReportType("User Activity");
        return summary;
    }
}
