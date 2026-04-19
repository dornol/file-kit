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

    /**
     * Returns a new incremental {@link ChecksumComputation} for streaming use.
     *
     * <p>The default implementation buffers all updates in memory and delegates to
     * {@link #checksum(byte[])} on {@link ChecksumComputation#finish()}. Override
     * this method for a true streaming implementation that does not retain file
     * content in memory — essential for large or untrusted inputs.</p>
     *
     * @return a fresh, non-thread-safe computation instance
     * @since 0.1.11
     */
    default ChecksumComputation newComputation() {
        return new BufferingComputation(this);
    }

}
