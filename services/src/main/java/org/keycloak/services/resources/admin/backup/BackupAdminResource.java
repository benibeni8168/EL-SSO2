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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

import org.jboss.logging.Logger;

/**
 * REST API for system configuration backups.
 * Base path: /admin/backups
 */
public class BackupAdminResource {

    private static final Logger logger = Logger.getLogger(BackupAdminResource.class);

    private final KeycloakSession session;
    private final AdminPermissionEvaluator auth;
    private final BackupService backupService;

    public BackupAdminResource(KeycloakSession session, AdminPermissionEvaluator auth) {
        this.session = session;
        this.auth = auth;
        this.backupService = new BackupService(session);
    }

    /**
     * Create a new backup.
     * POST /admin/backups
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createBackup(BackupRequest request) {
        // Require master realm manage permission
        auth.realm().requireManageRealm();

        try {
            String createdBy = auth.adminAuth().getUser().getUsername();
            BackupMetadata metadata = backupService.createBackup(
                    request != null ? request : new BackupRequest(),
                    createdBy
            );
            logger.infof("Backup created: %s by %s", metadata.getBackupId(), createdBy);
            return Response.status(Response.Status.CREATED).entity(metadata).build();
        } catch (BackupService.NotFoundException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (IOException e) {
            logger.error("Failed to create backup", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to create backup: " + e.getMessage()))
                    .build();
        } catch (RuntimeException e) {
            logger.error("Failed to create backup", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to create backup: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * List all backups.
     * GET /admin/backups
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listBackups() {
        auth.realm().requireViewRealm();

        try {
            List<BackupMetadata> backups = backupService.listBackups();
            return Response.ok(backups).build();
        } catch (IOException e) {
            logger.error("Failed to list backups", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to list backups"))
                    .build();
        }
    }

    /**
     * Get backup details.
     * GET /admin/backups/{backupId}
     */
    @GET
    @Path("/{backupId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBackup(@PathParam("backupId") String backupId) {
        auth.realm().requireViewRealm();

        try {
            BackupMetadata metadata = backupService.getBackup(backupId);
            if (metadata == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Backup not found"))
                        .build();
            }
            return Response.ok(metadata).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    /**
     * Download backup as ZIP file.
     * GET /admin/backups/{backupId}/download
     */
    @GET
    @Path("/{backupId}/download")
    @Produces("application/zip")
    public Response downloadBackup(@PathParam("backupId") String backupId) {
        auth.realm().requireManageRealm();

        try {
            java.nio.file.Path zipFile = backupService.createDownloadArchive(backupId);
            StreamingOutput stream = output -> {
                try (InputStream is = Files.newInputStream(zipFile)) {
                    is.transferTo(output);
                } finally {
                    // Clean up temp zip file after streaming
                    try {
                        Files.deleteIfExists(zipFile);
                    } catch (IOException e) {
                        logger.warnf("Failed to delete temp zip file: %s", zipFile);
                    }
                }
            };
            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + backupId + ".zip\"")
                    .header("Content-Length", Files.size(zipFile))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (BackupService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (IOException e) {
            logger.error("Failed to download backup", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to create download"))
                    .build();
        }
    }

    /**
     * Restore from a backup.
     * POST /admin/backups/{backupId}/restore
     */
    @POST
    @Path("/{backupId}/restore")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response restoreBackup(@PathParam("backupId") String backupId, RestoreRequest request) {
        auth.realm().requireManageRealm();

        try {
            List<String> restoredRealms = backupService.restoreBackup(
                    backupId,
                    request != null ? request : new RestoreRequest()
            );
            logger.infof("Backup restored: %s, realms: %s", backupId, restoredRealms);
            return Response.ok(restoredRealms).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (BackupService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (RuntimeException e) {
            logger.error("Failed to restore backup", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to restore backup: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete a backup.
     * DELETE /admin/backups/{backupId}
     */
    @DELETE
    @Path("/{backupId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBackup(@PathParam("backupId") String backupId) {
        auth.realm().requireManageRealm();

        try {
            backupService.deleteBackup(backupId);
            logger.infof("Backup deleted: %s", backupId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (BackupService.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (IOException e) {
            logger.error("Failed to delete backup", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to delete backup"))
                    .build();
        }
    }

    /**
     * Error response wrapper for consistent JSON error format.
     */
    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
