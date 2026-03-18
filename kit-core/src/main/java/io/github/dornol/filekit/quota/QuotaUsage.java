package io.github.dornol.filekit.quota;

/**
 * Snapshot of quota usage for a storage type and bucket.
 *
 * @param usedBytes current bytes used
 * @param maxBytes  maximum bytes allowed
 */
public record QuotaUsage(long usedBytes, long maxBytes) {

    /**
     * Returns the remaining bytes available, never negative.
     *
     * @return remaining bytes (0 if usage exceeds or equals the limit)
     */
    public long remainingBytes() {
        return Math.max(0, maxBytes - usedBytes);
    }
}
