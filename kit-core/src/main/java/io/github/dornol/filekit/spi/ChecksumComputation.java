package io.github.dornol.filekit.spi;

/**
 * Incremental checksum computation for streaming use.
 *
 * <p>Instances are <b>stateful</b> and <b>not thread-safe</b>.
 * Obtain a new instance via {@link ChecksumCalculator#newComputation()} for each computation.</p>
 *
 * <p>Typical lifecycle:
 * <pre>{@code
 * ChecksumComputation c = calc.newComputation();
 * while ((n = in.read(buf)) != -1) c.update(buf, 0, n);
 * String checksum = c.finish();
 * }</pre>
 *
 * <p>After {@link #finish()} is called, subsequent {@link #update(byte[], int, int)}
 * or {@link #finish()} calls must fail with {@link IllegalStateException}.</p>
 *
 * @since 0.1.11
 */
public interface ChecksumComputation {

    /**
     * Updates the computation with a portion of a byte buffer.
     *
     * @param buf source buffer
     * @param off start offset in {@code buf}
     * @param len number of bytes to consume from {@code off}
     * @throws IllegalStateException if {@link #finish()} was already called
     */
    void update(byte[] buf, int off, int len);

    /**
     * Finalizes the computation and returns the checksum string.
     *
     * @return checksum string (e.g. SHA-256 hex)
     * @throws IllegalStateException if called more than once
     */
    String finish();

}
