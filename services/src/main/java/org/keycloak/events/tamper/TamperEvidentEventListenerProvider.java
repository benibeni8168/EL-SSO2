package org.keycloak.events.tamper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

import org.jboss.logging.Logger;

/**
 * Adds a SHA-256 hash chain to all events for tamper detection.
 * Each event receives an "_integrity_hash" detail containing:
 *   SHA-256( previousHash + eventId + realmId + type + time + userId + ipAddress + error )
 *
 * When an HMAC secret key is configured, uses HMAC-SHA256 for stronger tamper protection.
 *
 * To verify integrity, recompute the chain from the first event.
 * Any modification to a stored event will produce a hash mismatch.
 */
public class TamperEvidentEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(TamperEvidentEventListenerProvider.class);
    static final String HASH_DETAIL_KEY = "_integrity_hash";

    private final AtomicReference<String> lastEventHash;
    private final AtomicReference<String> lastAdminEventHash;
    private final String hmacSecretKey;

    public TamperEvidentEventListenerProvider(
            AtomicReference<String> lastEventHash,
            AtomicReference<String> lastAdminEventHash,
            String hmacSecretKey) {
        this.lastEventHash = lastEventHash;
        this.lastAdminEventHash = lastAdminEventHash;
        this.hmacSecretKey = hmacSecretKey;
    }

    @Override
    public void onEvent(Event event) {
        Map<String, String> details = event.getDetails();
        if (details == null) {
            details = new java.util.HashMap<>();
            event.setDetails(details);
        }

        final Map<String, String> eventDetails = details;
        String hash = lastEventHash.updateAndGet(previous -> computeHmac(
                previous
                + safe(event.getId())
                + safe(event.getRealmId())
                + safe(event.getType() != null ? event.getType().name() : "")
                + event.getTime()
                + safe(event.getUserId())
                + safe(event.getIpAddress())
                + safe(event.getError())));

        eventDetails.put(HASH_DETAIL_KEY, hash);

        logger.tracef("Event tamper hash applied: id=%s hash=%s", event.getId(), hash);
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        Map<String, String> details = event.getDetails();
        if (details == null) {
            details = new java.util.HashMap<>();
            event.setDetails(details);
        }

        final Map<String, String> eventDetails = details;
        String hash = lastAdminEventHash.updateAndGet(previous -> computeHmac(
                previous
                + safe(event.getId())
                + safe(event.getRealmId())
                + safe(event.getResourceTypeAsString())
                + safe(event.getOperationType() != null ? event.getOperationType().name() : "")
                + event.getTime()
                + safe(event.getResourcePath())
                + safe(event.getError())));

        eventDetails.put(HASH_DETAIL_KEY, hash);

        logger.tracef("AdminEvent tamper hash applied: id=%s hash=%s", event.getId(), hash);
    }

    @Override
    public void close() {
        // no-op
    }

    static String safe(String s) {
        return s != null ? s : "";
    }

    /**
     * Computes HMAC-SHA256 if a secret key is configured, otherwise falls back to plain SHA-256.
     */
    String computeHmac(String input) {
        if (hmacSecretKey == null || hmacSecretKey.isEmpty()) {
            return sha256(input);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(hmacSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec — this cannot happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
