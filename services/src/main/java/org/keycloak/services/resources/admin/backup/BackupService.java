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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.keycloak.exportimport.ExportOptions;
import org.keycloak.exportimport.Strategy;
import org.keycloak.exportimport.util.ExportUtils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.RealmManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jboss.logging.Logger;

/**
 * Service for creating and managing backups.
 */
public class BackupService {

    private static final Logger logger = Logger.getLogger(BackupService.class);
    private static final String METADATA_FILE = "backup-metadata.json";
    // Only allow alphanumeric characters, hyphens, and underscores in backup IDs
    private static final Pattern VALID_BACKUP_ID = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final KeycloakSession session;
    private final Path backupDirectory;

    public BackupService(KeycloakSession session) {
        this.session = session;
        // Use system property or default
        String backupDir = System.getProperty("keycloak.backup.dir",
                System.getProperty("keycloak.home.dir", ".") + "/data/backups");
        this.backupDirectory = Paths.get(backupDir).toAbsolutePath().normalize();
    }

    public BackupMetadata createBackup(BackupRequest request, String createdBy) throws IOException {
        String requestedRealmName = request.getRealmName() == null ? null : request.getRealmName().trim();
        if (requestedRealmName != null && requestedRealmName.isEmpty()) {
            requestedRealmName = null;
        }

        // Create backup directory with timestamp precise enough to avoid same-second collisions.
        String backupId = "backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .format(Instant.now().atZone(ZoneOffset.UTC));
        Path backupPath = backupDirectory.resolve(backupId);

        List<String> realmNames = new ArrayList<>();
        ExportOptions options = new ExportOptions(
                request.isIncludeUsers(),
                request.isIncludeClients(),
                request.isIncludeRoles(),
                false,
                false
        );

        // Export realms
        Stream<RealmModel> realms;
        if (requestedRealmName != null) {
            RealmModel realm = session.realms().getRealmByName(requestedRealmName);
            if (realm == null) {
                throw new NotFoundException("Realm not found: " + requestedRealmName);
            }
            realms = Stream.of(realm);
        } else {
            realms = session.realms().getRealmsStream();
        }

        try {
            Files.createDirectories(backupPath);

            realms.forEach(realm -> {
                try {
                    RealmRepresentation rep = ExportUtils.exportRealm(session, realm, options, false);
                    Path realmFile = backupPath.resolve(realm.getName() + "-realm.json");
                    mapper.writeValue(realmFile.toFile(), rep);
                    realmNames.add(realm.getName());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to export realm: " + realm.getName(), e);
                }
            });

            // Calculate size
            long size;
            try (Stream<Path> pathStream = Files.walk(backupPath)) {
                size = pathStream
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> p.toFile().length())
                        .sum();
            }

            // Create metadata
            BackupMetadata metadata = new BackupMetadata();
            metadata.setBackupId(backupId);
            metadata.setDescription(request.getDescription());
            metadata.setCreatedAt(System.currentTimeMillis());
            metadata.setCreatedBy(createdBy);
            metadata.setRealms(realmNames);
            metadata.setSizeBytes(size);
            metadata.setStatus("COMPLETED");
            metadata.setDownloadUrl("/admin/backups/" + backupId + "/download");

            // Save metadata
            mapper.writeValue(backupPath.resolve(METADATA_FILE).toFile(), metadata);

            return metadata;
        } catch (IOException | RuntimeException e) {
            deleteDirectoryQuietly(backupPath);
            throw e;
        }
    }

    public List<BackupMetadata> listBackups() throws IOException {
        if (!Files.exists(backupDirectory)) {
            return Collections.emptyList();
        }
        try (Stream<Path> pathStream = Files.list(backupDirectory)) {
            return pathStream
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve(METADATA_FILE)))
                    .map(this::readMetadata)
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt())) // newest first
                    .collect(Collectors.toList());
        }
    }

    public BackupMetadata getBackup(String backupId) {
        validateBackupId(backupId);
        Path backupPath = backupDirectory.resolve(backupId);
        if (!Files.exists(backupPath.resolve(METADATA_FILE))) {
            return null;
        }
        return readMetadata(backupPath);
    }

    public Path createDownloadArchive(String backupId) throws IOException {
        validateBackupId(backupId);
        Path backupPath = backupDirectory.resolve(backupId);
        if (!Files.exists(backupPath)) {
            throw new NotFoundException("Backup not found");
        }

        Path zipFile = Files.createTempFile(backupId + "-", ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()));
             Stream<Path> pathStream = Files.walk(backupPath)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().endsWith(".zip"))
                    .forEach(p -> {
                        try {
                            zos.putNextEntry(new ZipEntry(backupPath.relativize(p).toString()));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return zipFile;
    }

    public void deleteBackup(String backupId) throws IOException {
        validateBackupId(backupId);
        Path backupPath = backupDirectory.resolve(backupId);
        if (!Files.exists(backupPath)) {
            throw new NotFoundException("Backup not found");
        }

        List<Path> pathsToDelete;
        try (Stream<Path> pathStream = Files.walk(backupPath)) {
            pathsToDelete = pathStream
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        }

        IOException failure = null;
        for (Path path : pathsToDelete) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                logger.warnf("Failed to delete file: %s", path);
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    public List<String> restoreBackup(String backupId, RestoreRequest request) {
        validateBackupId(backupId);
        Path backupPath = backupDirectory.resolve(backupId);
        if (!Files.exists(backupPath)) {
            throw new NotFoundException("Backup not found: " + backupId);
        }

        Strategy strategy = "OVERWRITE_EXISTING".equals(request.getStrategy())
                ? Strategy.OVERWRITE_EXISTING
                : Strategy.IGNORE_EXISTING;

        List<String> realmsToRestore = request.getRealmsToRestore();
        List<String> restoredRealms = new ArrayList<>();

        try (Stream<Path> pathStream = Files.list(backupPath)) {
            List<Path> realmFiles = pathStream
                    .filter(p -> p.getFileName().toString().endsWith("-realm.json"))
                    .collect(Collectors.toList());

            for (Path realmFile : realmFiles) {
                String fileName = realmFile.getFileName().toString();
                String realmName = fileName.substring(0, fileName.length() - "-realm.json".length());

                if (realmsToRestore != null && !realmsToRestore.isEmpty()
                        && !realmsToRestore.contains(realmName)) {
                    continue;
                }

                RealmRepresentation rep;
                try (InputStream is = Files.newInputStream(realmFile)) {
                    rep = mapper.readValue(is, RealmRepresentation.class);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read realm file: " + realmFile, e);
                }

                RealmManager realmManager = new RealmManager(session);
                RealmModel existing = session.realms().getRealmByName(realmName);
                if (existing != null) {
                    if (strategy == Strategy.IGNORE_EXISTING) {
                        logger.infof("Realm '%s' already exists. Skipping restore (IGNORE_EXISTING).", realmName);
                        continue;
                    } else {
                        logger.infof("Realm '%s' already exists. Removing before restore (OVERWRITE_EXISTING).", realmName);
                        realmManager.removeRealm(existing);
                    }
                }

                try {
                    realmManager.importRealm(rep);
                    restoredRealms.add(realmName);
                    logger.infof("Realm '%s' restored from backup '%s'.", realmName, backupId);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Failed to import realm: " + realmName, e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read backup directory: " + backupPath, e);
        }

        return restoredRealms;
    }

    private BackupMetadata readMetadata(Path backupPath) {
        try {
            return mapper.readValue(backupPath.resolve(METADATA_FILE).toFile(), BackupMetadata.class);
        } catch (IOException e) {
            logger.warnf("Failed to read backup metadata: %s", backupPath);
            return null;
        }
    }

    /**
     * Validates the backupId to prevent path traversal attacks.
     * @param backupId the backup ID to validate
     * @throws IllegalArgumentException if the backup ID is invalid
     */
    private void validateBackupId(String backupId) {
        if (backupId == null || backupId.isEmpty()) {
            throw new IllegalArgumentException("Backup ID cannot be null or empty");
        }
        if (!VALID_BACKUP_ID.matcher(backupId).matches()) {
            throw new IllegalArgumentException("Invalid backup ID format");
        }
        // Additional check: ensure resolved path is still within backup directory
        Path resolved = backupDirectory.resolve(backupId).toAbsolutePath().normalize();
        if (!resolved.startsWith(backupDirectory)) {
            throw new IllegalArgumentException("Invalid backup ID: path traversal detected");
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(directory)) {
            pathStream
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ioe) {
                            logger.warnf("Failed to clean up backup path after an error: %s", path);
                        }
                    });
        } catch (IOException ioe) {
            logger.warnf("Failed to clean up backup directory after an error: %s", directory);
        }
    }

    /**
     * NotFoundException helper for backup operations.
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
