package io.github.dornol.filekit.io;

import io.github.dornol.filekit.spi.ChecksumComputation;
import io.github.dornol.filekit.storage.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An {@link InputStream} decorator that incrementally computes a checksum while
 * the stream is being read and verifies it against an expected value upon reaching EOF.
 *
 * <p>Designed to replace the read-all-and-compare pattern used in file download
 * verification, enabling constant-memory (O(buffer)) checksum verification for
 * arbitrarily large files.</p>
 *
 * <p><b>Lifecycle:</b>
 * <ul>
 *   <li>Each successful read delegates to {@link ChecksumComputation#update(byte[], int, int)}.</li>
 *   <li>When the underlying stream returns {@code -1} (EOF), the computation is finalized
 *       and compared to the expected value. On mismatch, a
 *       {@link FileStorageException} with {@link FileStorageException#CHECKSUM_MISMATCH}
 *       is thrown from that {@code read} call.</li>
 *   <li>If the stream is closed before EOF, verification is <b>skipped</b> and a warning
 *       is logged. This accommodates partial-read scenarios (e.g. HTTP Range, preview).</li>
 *   <li>After verification completes successfully or is skipped, {@link #close()} is a no-op
 *       beyond closing the underlying stream.</li>
 * </ul>
 *
 * <p><b>Not thread-safe.</b> {@link #skip(long)} and {@code mark/reset} are intentionally
 * unsupported to prevent the computation from becoming inconsistent with the consumed bytes.</p>
 *
 * @since 0.1.11
 */
public final class ChecksumVerifyingInputStream extends FilterInputStream {

    private static final Logger log = LoggerFactory.getLogger(ChecksumVerifyingInputStream.class);

    private final ChecksumComputation computation;
    private final String expected;
    private final String fileKey;

    // Reused per-instance to avoid a fresh allocation on every single-byte read().
    // Safe because this class is documented as not thread-safe.
    private final byte[] singleByteBuf = new byte[1];

    // True once the stream has reached a terminal state, either successful
    // verification at EOF or skipped verification due to early close. Guards
    // against double verify/warn.
    private boolean done = false;

    /**
     * Wraps {@code in} with on-the-fly checksum verification.
     *
     * @param in          underlying stream (typically produced by a {@link io.github.dornol.filekit.storage.FileStorage})
     * @param computation fresh {@link ChecksumComputation} obtained from
     *                    {@link io.github.dornol.filekit.spi.ChecksumCalculator#newComputation()}
     * @param expected    expected checksum string (e.g. stored hash)
     * @param fileKey     file key for diagnostics (used in log/exception messages)
     */
    public ChecksumVerifyingInputStream(InputStream in,
                                        ChecksumComputation computation,
                                        String expected,
                                        String fileKey) {
        super(Objects.requireNonNull(in, "in"));
        this.computation = Objects.requireNonNull(computation, "computation");
        this.expected = Objects.requireNonNull(expected, "expected");
        this.fileKey = Objects.requireNonNull(fileKey, "fileKey");
    }

    @Override
    public int read() throws IOException {
        int v = super.read();
        if (v == -1) {
            verify();
        } else {
            singleByteBuf[0] = (byte) v;
            computation.update(singleByteBuf, 0, 1);
        }
        return v;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n == -1) {
            verify();
        } else if (n > 0) {
            computation.update(b, off, n);
        }
        return n;
    }

    /**
     * Unsupported — skipping bytes would break checksum verification.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public long skip(long n) {
        throw new UnsupportedOperationException("skip() would break checksum verification");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readlimit) {
        // no-op
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported by ChecksumVerifyingInputStream");
    }

    @Override
    public void close() throws IOException {
        try {
            if (!done) {
                done = true;
                log.warn("Checksum verification skipped (stream closed before EOF): key={}", fileKey);
            }
        } finally {
            super.close();
        }
    }

    private void verify() {
        if (done) {
            return;
        }
        done = true;
        String actual = computation.finish();
        if (!expected.equals(actual)) {
            throw new FileStorageException(FileStorageException.CHECKSUM_MISMATCH,
                    "Checksum mismatch for key=" + fileKey
                            + ": expected=" + expected + ", actual=" + actual);
        }
    }

}
