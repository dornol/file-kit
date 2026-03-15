package io.github.dornol.filekit.spi;

/**
 * Computes a checksum for file content, used for deduplication.
 */
public interface ChecksumCalculator {

    /**
     * Computes a checksum string for the given bytes.
     *
     * @param bytes file content
     * @return checksum string (e.g. SHA-256 hex)
     */
    String checksum(byte[] bytes);

}
