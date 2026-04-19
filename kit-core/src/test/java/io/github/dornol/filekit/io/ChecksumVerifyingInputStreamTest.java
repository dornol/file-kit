package io.github.dornol.filekit.io;

import io.github.dornol.filekit.spi.ChecksumComputation;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumVerifyingInputStreamTest {

    private static final Sha256ChecksumCalculator CALC = new Sha256ChecksumCalculator();

    // T1 — happy path with bulk read
    @Test
    void bulkRead_returnsContent_andVerifies() throws IOException {
        byte[] data = "hello world".getBytes();
        String hash = CALC.checksum(data);

        try (InputStream in = wrap(data, hash)) {
            byte[] actual = in.readAllBytes();
            assertArrayEquals(data, actual);
        }
    }

    // T2 — happy path byte-by-byte
    @Test
    void byteByByte_returnsContent_andVerifies() throws IOException {
        byte[] data = "abc".getBytes();
        String hash = CALC.checksum(data);

        try (InputStream in = wrap(data, hash)) {
            assertEquals('a', in.read());
            assertEquals('b', in.read());
            assertEquals('c', in.read());
            assertEquals(-1, in.read()); // triggers verify
        }
    }

    // T3 — mismatch at EOF (wrong expected)
    @Test
    void mismatch_throwsOnEof() throws IOException {
        byte[] data = "hello".getBytes();
        try (InputStream in = wrap(data, "deadbeef")) {
            FileStorageException ex = assertThrows(FileStorageException.class, in::readAllBytes);
            assertEquals(FileStorageException.CHECKSUM_MISMATCH, ex.getMessageKey());
            assertTrue(ex.getMessage().contains("file-key"));
            assertTrue(ex.getMessage().contains("expected=deadbeef"));
        }
    }

    // T4 — mismatch not raised until EOF is observed
    @Test
    void mismatch_notRaisedMidStream() throws IOException {
        byte[] data = "hello".getBytes();
        try (InputStream in = wrap(data, "deadbeef")) {
            byte[] buf = new byte[3];
            int n = in.read(buf); // partial read
            assertEquals(3, n);
            // No exception yet; only at EOF.
        }
    }

    // T5 — early close skips verification without exception
    @Test
    void earlyClose_skipsVerification() throws IOException {
        byte[] data = "hello".getBytes();
        TrackingInputStream inner = new TrackingInputStream(new ByteArrayInputStream(data));
        ChecksumVerifyingInputStream in = new ChecksumVerifyingInputStream(
                inner, CALC.newComputation(), "will-not-match", "file-key");

        byte[] buf = new byte[2];
        in.read(buf);
        in.close();

        assertTrue(inner.closed.get(), "underlying stream must be closed");
    }

    // T6 — close after EOF is no-op
    @Test
    void closeAfterEof_noOp() throws IOException {
        byte[] data = "x".getBytes();
        String hash = CALC.checksum(data);
        TrackingInputStream inner = new TrackingInputStream(new ByteArrayInputStream(data));
        ChecksumVerifyingInputStream in = new ChecksumVerifyingInputStream(
                inner, CALC.newComputation(), hash, "file-key");

        in.readAllBytes();
        in.close();
        assertTrue(inner.closed.get());
    }

    // T7 — close closes underlying even after mismatch exception
    @Test
    void closeAfterMismatch_closesUnderlying() throws IOException {
        byte[] data = "hello".getBytes();
        TrackingInputStream inner = new TrackingInputStream(new ByteArrayInputStream(data));
        ChecksumVerifyingInputStream in = new ChecksumVerifyingInputStream(
                inner, CALC.newComputation(), "bad-hash", "file-key");

        assertThrows(FileStorageException.class, in::readAllBytes);
        in.close();
        assertTrue(inner.closed.get(), "underlying stream must be closed even after verify failure");
    }

    // T8 — skip is unsupported
    @Test
    void skip_unsupported() throws IOException {
        try (InputStream in = wrap("abc".getBytes(), "any")) {
            assertThrows(UnsupportedOperationException.class, () -> in.skip(1));
        }
    }

    // T9 — markSupported false; reset throws IOException; mark no-op
    @Test
    void markAndReset_unsupported() throws IOException {
        try (InputStream in = wrap("abc".getBytes(), "any")) {
            assertFalse(in.markSupported());
            in.mark(10); // should not throw
            assertThrows(IOException.class, in::reset);
        }
    }

    // T10 — double verify (read past EOF twice) is idempotent
    @Test
    void readAfterEof_idempotent() throws IOException {
        byte[] data = "x".getBytes();
        String hash = CALC.checksum(data);
        try (InputStream in = wrap(data, hash)) {
            assertEquals('x', in.read());
            assertEquals(-1, in.read());
            assertEquals(-1, in.read()); // second -1 must not refinalize
        }
    }

    // T11 — IOException from underlying propagates transparently
    @Test
    void underlyingIoException_propagates() {
        InputStream bad = new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        ChecksumVerifyingInputStream in = new ChecksumVerifyingInputStream(
                bad, CALC.newComputation(), "any", "file-key");

        assertThrows(IOException.class, in::read);
    }

    // T12 — empty stream verifies against SHA-256 of empty byte[]
    @Test
    void emptyStream_verifiesEmptyHash() throws IOException {
        String emptyHash = CALC.checksum(new byte[0]);
        try (InputStream in = wrap(new byte[0], emptyHash)) {
            assertEquals(-1, in.read());
        }
    }

    // Null-check coverage
    @Test
    void nullArgs_throw() {
        ChecksumComputation comp = CALC.newComputation();
        InputStream dummy = new ByteArrayInputStream(new byte[0]);
        assertThrows(NullPointerException.class,
                () -> new ChecksumVerifyingInputStream(null, comp, "e", "k"));
        assertThrows(NullPointerException.class,
                () -> new ChecksumVerifyingInputStream(dummy, null, "e", "k"));
        assertThrows(NullPointerException.class,
                () -> new ChecksumVerifyingInputStream(dummy, comp, null, "k"));
        assertThrows(NullPointerException.class,
                () -> new ChecksumVerifyingInputStream(dummy, comp, "e", null));
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static ChecksumVerifyingInputStream wrap(byte[] data, String expected) {
        return new ChecksumVerifyingInputStream(
                new ByteArrayInputStream(data), CALC.newComputation(), expected, "file-key");
    }

    private static final class TrackingInputStream extends InputStream {
        private final InputStream delegate;
        final AtomicBoolean closed = new AtomicBoolean(false);

        TrackingInputStream(InputStream delegate) { this.delegate = delegate; }

        @Override public int read() throws IOException { return delegate.read(); }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }
        @Override public void close() throws IOException {
            closed.set(true);
            delegate.close();
        }
    }
}
