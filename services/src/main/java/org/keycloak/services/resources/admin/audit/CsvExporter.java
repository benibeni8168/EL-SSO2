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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;

/**
 * Utility class for exporting events to CSV format.
 */
public class CsvExporter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String CSV_SEPARATOR = ",";

    private CsvExporter() {
        // Utility class
    }

    /**
     * Exports a list of events to CSV format.
     *
     * @param events the events to export
     * @param out the output stream to write to
     */
    public static void exportEvents(List<Event> events, OutputStream out) {
        PrintWriter writer = new PrintWriter(out);
        
        // Write header
        writer.println("id,time,type,realmId,clientId,userId,sessionId,ipAddress,error,details");
        
        // Write rows
        for (Event event : events) {
            writer.println(formatEventRow(event));
        }
        
        writer.flush();
    }

    /**
     * Exports a list of admin events to CSV format.
     *
     * @param events the admin events to export
     * @param out the output stream to write to
     */
    public static void exportAdminEvents(List<AdminEvent> events, OutputStream out) {
        PrintWriter writer = new PrintWriter(out);
        
        // Write header
        writer.println("id,time,realmId,operationType,resourceType,resourcePath,authRealmId,authClientId,authUserId,authIpAddress,error");
        
        // Write rows
        for (AdminEvent event : events) {
            writer.println(formatAdminEventRow(event));
        }
        
        writer.flush();
    }

    private static String formatEventRow(Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append(escapeCsv(event.getId())).append(CSV_SEPARATOR);
        sb.append(formatTime(event.getTime())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getType() != null ? event.getType().name() : "")).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getRealmId())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getClientId())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getUserId())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getSessionId())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getIpAddress())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getError())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(formatDetails(event.getDetails())));
        return sb.toString();
    }

    private static String formatAdminEventRow(AdminEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(escapeCsv(event.getId())).append(CSV_SEPARATOR);
        sb.append(formatTime(event.getTime())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getRealmId())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getOperationType() != null ? event.getOperationType().name() : "")).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getResourceTypeAsString())).append(CSV_SEPARATOR);
        sb.append(escapeCsv(event.getResourcePath())).append(CSV_SEPARATOR);
        
        if (event.getAuthDetails() != null) {
            sb.append(escapeCsv(event.getAuthDetails().getRealmId())).append(CSV_SEPARATOR);
            sb.append(escapeCsv(event.getAuthDetails().getClientId())).append(CSV_SEPARATOR);
            sb.append(escapeCsv(event.getAuthDetails().getUserId())).append(CSV_SEPARATOR);
            sb.append(escapeCsv(event.getAuthDetails().getIpAddress())).append(CSV_SEPARATOR);
        } else {
            sb.append(CSV_SEPARATOR).append(CSV_SEPARATOR).append(CSV_SEPARATOR).append(CSV_SEPARATOR);
        }
        
        sb.append(escapeCsv(event.getError()));
        return sb.toString();
    }

    private static String formatTime(long timeInMillis) {
        return Instant.ofEpochMilli(timeInMillis).atOffset(ZoneOffset.UTC).format(DATE_FORMATTER);
    }

    private static String formatDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, newline, carriage return, or quote, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
