package org.keycloak.events;

/**
 * Result of verifying the SHA-256 integrity hashes of stored audit events.
 */
public class EventIntegrityResult {

    private final long checked;
    private final long valid;
    private final long tampered;
    private final long legacyNoHash;

    public EventIntegrityResult(long checked, long valid, long tampered, long legacyNoHash) {
        this.checked = checked;
        this.valid = valid;
        this.tampered = tampered;
        this.legacyNoHash = legacyNoHash;
    }

    /** Total number of event rows inspected. */
    public long getChecked() {
        return checked;
    }

    /** Number of rows whose stored hash matches the recomputed hash. */
    public long getValid() {
        return valid;
    }

    /** Number of rows whose stored hash does NOT match — indicates tampering. */
    public long getTampered() {
        return tampered;
    }

    /** Number of rows with a NULL hash (stored before integrity protection was enabled). */
    public long getLegacyNoHash() {
        return legacyNoHash;
    }
}
