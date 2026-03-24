package org.keycloak.events.tamper;

/**
 * Result of verifying a tamper-evident event chain.
 */
public class VerificationResult {

    public enum Status {
        /** The event chain is valid - all hashes match */
        VALID,
        /** The event chain is invalid - hash mismatch detected */
        INVALID,
        /** The event chain is empty - nothing to verify */
        EMPTY
    }

    private final Status status;
    private final int failedAtIndex;
    private final String expectedHash;
    private final String actualHash;

    private VerificationResult(Status status, int failedAtIndex, String expectedHash, String actualHash) {
        this.status = status;
        this.failedAtIndex = failedAtIndex;
        this.expectedHash = expectedHash;
        this.actualHash = actualHash;
    }

    /**
     * Creates a result indicating the chain is valid.
     */
    public static VerificationResult valid() {
        return new VerificationResult(Status.VALID, -1, null, null);
    }

    /**
     * Creates a result indicating the chain is invalid.
     * 
     * @param index the index of the event where verification failed
     * @param expected the expected hash value
     * @param actual the actual hash value found in the event
     */
    public static VerificationResult invalid(int index, String expected, String actual) {
        return new VerificationResult(Status.INVALID, index, expected, actual);
    }

    /**
     * Creates a result indicating the chain is empty.
     */
    public static VerificationResult empty() {
        return new VerificationResult(Status.EMPTY, -1, null, null);
    }

    /**
     * Returns the verification status.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the index of the event where verification failed, or -1 if valid/empty.
     */
    public int getFailedAtIndex() {
        return failedAtIndex;
    }

    /**
     * Returns the expected hash value at the failed index, or null if valid/empty.
     */
    public String getExpectedHash() {
        return expectedHash;
    }

    /**
     * Returns the actual hash value found at the failed index, or null if valid/empty.
     */
    public String getActualHash() {
        return actualHash;
    }

    /**
     * Returns true if the chain is valid.
     */
    public boolean isValid() {
        return status == Status.VALID;
    }

    @Override
    public String toString() {
        return switch (status) {
            case VALID -> "VerificationResult[VALID]";
            case EMPTY -> "VerificationResult[EMPTY]";
            case INVALID -> String.format("VerificationResult[INVALID at index %d: expected=%s, actual=%s]",
                    failedAtIndex, expectedHash, actualHash);
        };
    }
}
