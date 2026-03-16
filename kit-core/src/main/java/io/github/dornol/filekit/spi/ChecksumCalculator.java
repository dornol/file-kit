package io.github.dornol.filekit.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

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

    /**
     * Computes a checksum string from an input stream.
     *
     * <p>The default implementation reads all bytes into memory.
     * Override for a streaming implementation that avoids full buffering.</p>
     *
     * @param inputStream file content stream
     * @return checksum string (e.g. SHA-256 hex)
     */
    default String checksum(InputStream inputStream) {
        try {
            return checksum(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
