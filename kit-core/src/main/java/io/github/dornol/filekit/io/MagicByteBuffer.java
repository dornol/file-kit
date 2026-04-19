package io.github.dornol.filekit.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Accumulates the first N bytes observed from a stream for file-format detection,
 * without retaining the remainder of the stream.
 *
 * <p>Intended for use alongside a write path that tees incoming bytes through
 * {@link #observe(byte[], int, int)}. Once the buffer reaches its configured
 * capacity, subsequent observations are silently ignored — only the header is
 * retained.</p>
 *
 * <p>Typical usage:
 * <pre>{@code
 * MagicByteBuffer header = new MagicByteBuffer();
 * try (InputStream in = source.getInputStream();
 *      OutputStream out = Files.newOutputStream(tempFile)) {
 *     byte[] buf = new byte[8192];
 *     int n;
 *     while ((n = in.read(buf)) != -1) {
 *         out.write(buf, 0, n);
 *         header.observe(buf, 0, n);
 *     }
 * }
 * FileFormat format = formatExtractor.extract(header.asInputStream());
 * }</pre>
 *
 * <p><b>Not thread-safe.</b> Safe for a single-producer write phase followed
 * by a single-consumer read phase via {@link #asInputStream()}.</p>
 *
 * @since 0.1.12
 */
public final class MagicByteBuffer {

    /** Default capacity (16 KiB), matching Apache Tika's default detector peek. */
    public static final int DEFAULT_SIZE = 16 * 1024;

    /** Minimum allowed capacity (1 KiB). */
    public static final int MIN_SIZE = 1024;

    private final byte[] buffer;
    private int size = 0;

    /** Creates a buffer with {@link #DEFAULT_SIZE} capacity. */
    public MagicByteBuffer() {
        this(DEFAULT_SIZE);
    }

    /**
     * @param capacity buffer capacity in bytes
     * @throws IllegalArgumentException if {@code capacity < MIN_SIZE}
     */
    public MagicByteBuffer(int capacity) {
        if (capacity < MIN_SIZE) {
            throw new IllegalArgumentException(
                    "capacity must be at least " + MIN_SIZE + ", got " + capacity);
        }
        this.buffer = new byte[capacity];
    }

    /**
     * Copies up to {@code len} bytes from {@code buf[off..off+len)} into the
     * internal buffer, until capacity is reached. Bytes beyond capacity are
     * silently ignored.
     */
    public void observe(byte[] buf, int off, int len) {
        if (size >= buffer.length || len <= 0) {
            return;
        }
        int remaining = buffer.length - size;
        int toCopy = Math.min(remaining, len);
        System.arraycopy(buf, off, buffer, size, toCopy);
        size += toCopy;
    }

    /** Returns the number of bytes captured so far. */
    public int size() {
        return size;
    }

    /** Returns the buffer capacity. */
    public int capacity() {
        return buffer.length;
    }

    /**
     * Returns a new {@link InputStream} over a defensive copy of the captured bytes.
     *
     * <p>The stream reads only what {@link #observe} actually captured — if the
     * source was shorter than capacity, the stream is correspondingly short.
     * Callers that need to detect "source fully fit in buffer" can compare
     * {@link #size()} to the original file size.</p>
     *
     * <p>The header is bounded to at most {@link #capacity()} bytes (default 16 KiB),
     * so the copy is cheap. Defensive copying ensures the returned stream is
     * independent of any subsequent {@link #observe} calls on this buffer.</p>
     */
    public InputStream asInputStream() {
        return new ByteArrayInputStream(Arrays.copyOf(buffer, size));
    }

}
