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

import jakarta.ws.rs.Path;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

/**
 * Root resource for audit and compliance reports.
 * Provides access to various report sub-resources.
 */
public class AuditReportsResource {

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;

    public AuditReportsResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
    }

    /**
     * Returns the authentication reports sub-resource.
     * Provides reports on login, logout, registration events.
     */
    @Path("/authentication")
    public AuthenticationReportResource authenticationReports() {
        return new AuthenticationReportResource(session, realm, auth);
    }

    /**
     * Returns the admin activity reports sub-resource.
     * Provides reports on admin operations like user/client management.
     */
    @Path("/admin-activity")
    public AdminActivityReportResource adminActivityReports() {
        return new AdminActivityReportResource(session, realm, auth);
    }

    /**
     * Returns the security reports sub-resource.
     * Provides reports on security-sensitive events like password changes, lockouts.
     */
    @Path("/security")
    public SecurityReportResource securityReports() {
        return new SecurityReportResource(session, realm, auth);
    }

    /**
     * Returns the user activity reports sub-resource.
     * Provides per-user audit trail for GDPR and compliance.
     */
    @Path("/user-activity")
    public UserActivityReportResource userActivityReports() {
        return new UserActivityReportResource(session, realm, auth);
    }
}
