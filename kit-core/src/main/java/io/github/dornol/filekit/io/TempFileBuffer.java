package io.github.dornol.filekit.io;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link Path} to a freshly created temporary file, scoped to a
 * try-with-resources block. {@link #close()} deletes the file best-effort.
 *
 * <p>Intended for scratchpad usage inside a single method call. Pair with
 * {@code try-with-resources} so the file is removed on any control-flow
 * path — normal return, thrown exception, or nested block exit.</p>
 *
 * <p>Typical usage:
 * <pre>{@code
 * try (TempFileBuffer tempFile = TempFileBuffer.create("file-kit-upload-")) {
 *     // ... work on tempFile.path() ...
 * } // file is deleted here
 * }</pre>
 *
 * <p><b>Not thread-safe.</b> Each caller should own its own instance.</p>
 *
 * @since 0.1.13
 */
public final class TempFileBuffer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(TempFileBuffer.class);
    private static final String SUFFIX = ".tmp";

    private final Path path;
    private boolean closed = false;

    /**
     * Creates a new temporary file in the system temp directory with the given
     * prefix and a {@code .tmp} suffix.
     *
     * @param prefix temp file name prefix (must not be null)
     * @throws IOException          if the file cannot be created
     * @throws NullPointerException if {@code prefix} is null
     */
    public static TempFileBuffer create(String prefix) throws IOException {
        return create(null, prefix);
    }

    /**
     * Creates a new temporary file in the given directory with the given prefix
     * and a {@code .tmp} suffix. When {@code directory} is {@code null}, the
     * system temp directory is used (same as {@link #create(String)}).
     *
     * <p>If {@code directory} does not exist, {@link Files#createTempFile(Path, String, String, java.nio.file.attribute.FileAttribute[])}
     * will raise {@link java.nio.file.NoSuchFileException} — the caller is
     * expected to ensure the directory is prepared.</p>
     *
     * @param directory directory to create the temp file in, or {@code null} for system temp
     * @param prefix    temp file name prefix (must not be null)
     * @throws IOException          if the file cannot be created
     * @throws NullPointerException if {@code prefix} is null
     * @since 0.1.25
     */
    public static TempFileBuffer create(@Nullable Path directory, String prefix) throws IOException {
        Objects.requireNonNull(prefix, "prefix");
        Path path = directory != null
                ? Files.createTempFile(directory, prefix, SUFFIX)
                : Files.createTempFile(prefix, SUFFIX);
        return new TempFileBuffer(path);
    }

    private TempFileBuffer(Path path) {
        this.path = path;
    }

    /** Returns the underlying path. Remains non-null after {@link #close()}. */
    public Path path() {
        return path;
    }

    /**
     * Transfers ownership of the underlying file to the caller. After this
     * call, {@link #close()} is a no-op — the file is <b>not</b> deleted on
     * try-with-resources exit. The caller becomes responsible for the file's
     * lifecycle.
     *
     * <p>Intended for cases where the temp file must outlive the
     * {@code try-with-resources} block, typically by wrapping it into a
     * {@link DeleteOnCloseInputStream} that deletes on stream close.</p>
     *
     * <p>Usage:
     * <pre>{@code
     * try (TempFileBuffer buf = TempFileBuffer.create("prefix-")) {
     *     // ... populate buf.path() ...
     *     return new DeleteOnCloseInputStream(buf.release());
     * }
     * // On exception here, buf.close() fires and deletes the file
     * // (because release() was not reached).
     * }</pre>
     *
     * @return the underlying {@link Path} — same instance as {@link #path()}
     * @throws IllegalStateException if this buffer is already closed or released
     * @since 0.1.15
     */
    public Path release() {
        if (closed) {
            throw new IllegalStateException("TempFileBuffer already closed or released");
        }
        closed = true;
        return path;
    }

    /**
     * Deletes the file best-effort. Safe to call more than once.
     *
     * <p>Any {@link IOException} from the delete is logged at WARN level
     * and swallowed — {@code close()} never throws.</p>
     *
     * <p>If {@link #release()} was called, this is a no-op and the file is
     * left in place.</p>
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {} ({})", path, e.getMessage());
        }
    }
}
