package org.keycloak.services.resources.admin.integrity;

/**
 * JSON representation of an event integrity verification result.
 */
public class EventIntegrityRepresentation {

    private long checked;
    private long valid;
    private long tampered;
    private long legacyNoHash;

    public EventIntegrityRepresentation() {}

    public EventIntegrityRepresentation(long checked, long valid, long tampered, long legacyNoHash) {
        this.checked = checked;
        this.valid = valid;
        this.tampered = tampered;
        this.legacyNoHash = legacyNoHash;
    }

    public long getChecked() {
        return checked;
    }

    public void setChecked(long checked) {
        this.checked = checked;
    }

    public long getValid() {
        return valid;
    }

    public void setValid(long valid) {
        this.valid = valid;
    }

    public long getTampered() {
        return tampered;
    }

    public void setTampered(long tampered) {
        this.tampered = tampered;
    }

    public long getLegacyNoHash() {
        return legacyNoHash;
    }

    public void setLegacyNoHash(long legacyNoHash) {
        this.legacyNoHash = legacyNoHash;
    }
}
