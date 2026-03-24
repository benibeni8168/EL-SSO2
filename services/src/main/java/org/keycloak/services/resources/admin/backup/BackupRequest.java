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
package org.keycloak.services.resources.admin.backup;

/**
 * Request model for creating a backup.
 * Frontend-friendly with clear field names and defaults.
 */
public class BackupRequest {

    private String realmName;           // null = all realms
    private boolean includeUsers = true;
    private boolean includeClients = true;
    private boolean includeRoles = true;
    private String description;

    public BackupRequest() {
    }

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    public boolean isIncludeUsers() {
        return includeUsers;
    }

    public void setIncludeUsers(boolean includeUsers) {
        this.includeUsers = includeUsers;
    }

    public boolean isIncludeClients() {
        return includeClients;
    }

    public void setIncludeClients(boolean includeClients) {
        this.includeClients = includeClients;
    }

    public boolean isIncludeRoles() {
        return includeRoles;
    }

    public void setIncludeRoles(boolean includeRoles) {
        this.includeRoles = includeRoles;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
