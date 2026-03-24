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

import java.util.Map;

/**
 * Model class for audit report summaries.
 */
public class ReportSummary {

    private long totalEvents;
    private long successCount;
    private long failureCount;
    private Map<String, Long> countsByType;
    private String dateFrom;
    private String dateTo;
    private String generatedAt;
    private String generatedByUserId;
    private String realmName;
    private String reportType;

    public ReportSummary() {
    }

    public ReportSummary(long totalEvents, long successCount, long failureCount, 
                         Map<String, Long> countsByType, String dateFrom, String dateTo) {
        this.totalEvents = totalEvents;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.countsByType = countsByType;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(long failureCount) {
        this.failureCount = failureCount;
    }

    public Map<String, Long> getCountsByType() {
        return countsByType;
    }

    public void setCountsByType(Map<String, Long> countsByType) {
        this.countsByType = countsByType;
    }

    public String getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

    public String getDateTo() {
        return dateTo;
    }

    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getGeneratedByUserId() {
        return generatedByUserId;
    }

    public void setGeneratedByUserId(String generatedByUserId) {
        this.generatedByUserId = generatedByUserId;
    }

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }
}
