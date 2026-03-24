package org.keycloak.events.jpa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes SHA-256 integrity hashes for stored audit event entities.
 *
 * <p>The canonical string is a pipe-separated concatenation of all persisted fields in a
 * fixed order. Null field values are represented as the empty string.
 */
final class EventIntegrityUtil {

    private EventIntegrityUtil() {}

    /**
     * Computes the integrity hash for a user event entity.
     * All fields must be set before calling this method.
     */
    static String computeHash(EventEntity e) {
        String canonical = String.join(
                "|",
                n(e.getId()),
                Long.toString(e.getTime()),
                n(e.getType()),
                n(e.getRealmId()),
                n(e.getClientId()),
                n(e.getUserId()),
                n(e.getSessionId()),
                n(e.getIpAddress()),
                n(e.getError()),
                n(e.getDetailsJson()));
        return sha256Hex(canonical);
    }

    /**
     * Computes the integrity hash for an admin event entity.
     * All fields must be set before calling this method.
     */
    static String computeHash(AdminEventEntity e) {
        String canonical = String.join(
                "|",
                n(e.getId()),
                Long.toString(e.getTime()),
                n(e.getRealmId()),
                n(e.getOperationType()),
                n(e.getResourceType()),
                n(e.getAuthRealmId()),
                n(e.getAuthClientId()),
                n(e.getAuthUserId()),
                n(e.getAuthIpAddress()),
                n(e.getResourcePath()),
                n(e.getRepresentation()),
                n(e.getError()),
                n(e.getDetailsJson()));
        return sha256Hex(canonical);
    }

    private static String n(String value) {
        return value != null ? value : "";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE specification and is always present
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
