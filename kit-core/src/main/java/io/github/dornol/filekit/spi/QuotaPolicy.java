package io.github.dornol.filekit.spi;

/**
 * SPI for defining storage quota limits per storage type and bucket.
 *
 * <p>Applications implement this interface to provide their own quota rules
 * (e.g. per-user, per-tenant limits mapped from storageType/bucket).</p>
 */
public interface QuotaPolicy {

    /**
     * Returns the maximum allowed bytes for the given storage type and bucket.
     *
     * @param storageType the storage backend type
     * @param bucket      the target bucket
     * @return maximum bytes allowed
     */
    long getMaxBytes(Enum<?> storageType, String bucket);
}
