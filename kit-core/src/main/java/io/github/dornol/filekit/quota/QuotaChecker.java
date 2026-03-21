package io.github.dornol.filekit.quota;

import io.github.dornol.filekit.spi.QuotaPolicy;
import io.github.dornol.filekit.spi.QuotaUsageProvider;
import io.github.dornol.filekit.storage.FileStorageException;

import java.util.Objects;

/**
 * Checks whether an upload would exceed the configured quota.
 *
 * <p>Combines a {@link QuotaPolicy} (limit definition) with a
 * {@link QuotaUsageProvider} (current usage) to enforce storage limits.</p>
 */
public class QuotaChecker {

    private final QuotaPolicy policy;
    private final QuotaUsageProvider usageProvider;

    /**
     * @param policy        provides the maximum allowed bytes
     * @param usageProvider provides the current bytes used
     */
    public QuotaChecker(QuotaPolicy policy, QuotaUsageProvider usageProvider) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.usageProvider = Objects.requireNonNull(usageProvider, "usageProvider");
    }

    /**
     * Checks that adding {@code additionalBytes} would not exceed the quota.
     *
     * @param storageType    the storage backend type
     * @param bucket         the target bucket
     * @param additionalBytes bytes to be added
     * @throws FileStorageException with {@link FileStorageException#QUOTA_EXCEEDED} if quota would be exceeded
     */
    public void check(Enum<?> storageType, String bucket, long additionalBytes) {
        long maxBytes = policy.getMaxBytes(storageType, bucket);
        long usedBytes = usageProvider.getUsedBytes(storageType, bucket);

        long totalBytes;
        try {
            totalBytes = Math.addExact(usedBytes, additionalBytes);
        } catch (ArithmeticException e) {
            totalBytes = Long.MAX_VALUE;
        }
        if (totalBytes > maxBytes) {
            throw new FileStorageException(FileStorageException.QUOTA_EXCEEDED,
                    "Quota exceeded: used=" + usedBytes + ", additional=" + additionalBytes
                            + ", max=" + maxBytes);
        }
    }

    /**
     * Returns the current quota usage snapshot.
     *
     * @param storageType the storage backend type
     * @param bucket      the target bucket
     * @return current usage
     */
    public QuotaUsage getUsage(Enum<?> storageType, String bucket) {
        long maxBytes = policy.getMaxBytes(storageType, bucket);
        long usedBytes = usageProvider.getUsedBytes(storageType, bucket);
        return new QuotaUsage(usedBytes, maxBytes);
    }
}
