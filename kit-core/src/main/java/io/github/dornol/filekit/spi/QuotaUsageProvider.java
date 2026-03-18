package io.github.dornol.filekit.spi;

/**
 * SPI for retrieving current storage usage per storage type and bucket.
 *
 * <p>Applications implement this interface to report how many bytes
 * are currently consumed (e.g. by querying a database or cache).</p>
 */
public interface QuotaUsageProvider {

    /**
     * Returns the number of bytes currently used for the given storage type and bucket.
     *
     * @param storageType the storage backend type
     * @param bucket      the target bucket
     * @return bytes currently used
     */
    long getUsedBytes(Enum<?> storageType, String bucket);
}
