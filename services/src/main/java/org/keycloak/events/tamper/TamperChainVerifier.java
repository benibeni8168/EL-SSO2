package org.keycloak.events.tamper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;

/**
 * Verifies the integrity of tamper-evident event chains.
 * 
 * <p>This utility recomputes the hash chain for a list of events and compares
 * the computed hashes against the stored "_integrity_hash" values. If any hash
 * does not match, the chain has been tampered with.</p>
 * 
 * <p>Usage example:</p>
 * <pre>
 * TamperChainVerifier verifier = new TamperChainVerifier(hmacSecretKey);
 * VerificationResult result = verifier.verifyEvents(events);
 * if (!result.isValid()) {
 *     // Handle tampering
 * }
 * </pre>
 */
public class TamperChainVerifier {

    private final String hmacSecretKey;

    /**
     * Creates a verifier with the specified HMAC secret key.
     * 
     * @param hmacSecretKey the HMAC secret key, or null/empty to use plain SHA-256
     */
    public TamperChainVerifier(String hmacSecretKey) {
        this.hmacSecretKey = hmacSecretKey;
    }

    /**
     * Verify a chain of regular events.
     * 
     * @param events the list of events to verify, in chronological order
     * @return VerificationResult indicating if chain is valid
     */
    public VerificationResult verifyEvents(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return VerificationResult.empty();
        }

        String previousHash = "";

        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);

            String computedHash = computeHash(
                    previousHash
                    + safe(event.getId())
                    + safe(event.getRealmId())
                    + safe(event.getType() != null ? event.getType().name() : "")
                    + event.getTime()
                    + safe(event.getUserId())
                    + safe(event.getIpAddress())
                    + safe(event.getError()));

            String storedHash = getStoredHash(event.getDetails());

            if (!constantTimeEquals(computedHash, storedHash)) {
                return VerificationResult.invalid(i, computedHash, storedHash);
            }

            previousHash = computedHash;
        }

        return VerificationResult.valid();
    }

    /**
     * Verify a chain of admin events.
     * 
     * @param events the list of admin events to verify, in chronological order
     * @return VerificationResult indicating if chain is valid
     */
    public VerificationResult verifyAdminEvents(List<AdminEvent> events) {
        if (events == null || events.isEmpty()) {
            return VerificationResult.empty();
        }

        String previousHash = "";

        for (int i = 0; i < events.size(); i++) {
            AdminEvent event = events.get(i);

            String computedHash = computeHash(
                    previousHash
                    + safe(event.getId())
                    + safe(event.getRealmId())
                    + safe(event.getResourceTypeAsString())
                    + safe(event.getOperationType() != null ? event.getOperationType().name() : "")
                    + event.getTime()
                    + safe(event.getResourcePath())
                    + safe(event.getError()));

            String storedHash = getStoredHash(event.getDetails());

            if (!constantTimeEquals(computedHash, storedHash)) {
                return VerificationResult.invalid(i, computedHash, storedHash);
            }

            previousHash = computedHash;
        }

        return VerificationResult.valid();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * Returns false if either argument is null.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private String getStoredHash(Map<String, String> details) {
        if (details == null) {
            return null;
        }
        return details.get(TamperEvidentEventListenerProvider.HASH_DETAIL_KEY);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    /**
     * Computes HMAC-SHA256 if a secret key is configured, otherwise falls back to plain SHA-256.
     */
    private String computeHash(String input) {
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

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
