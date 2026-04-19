package io.github.dornol.filekit.io;

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
     * Creates a new temporary file with the given prefix and a {@code .tmp} suffix.
     *
     * @param prefix temp file name prefix (must not be null)
     * @throws IOException          if the file cannot be created
     * @throws NullPointerException if {@code prefix} is null
     */
    public static TempFileBuffer create(String prefix) throws IOException {
        Objects.requireNonNull(prefix, "prefix");
        return new TempFileBuffer(Files.createTempFile(prefix, SUFFIX));
    }

    private TempFileBuffer(Path path) {
        this.path = path;
    }

    /** Returns the underlying path. Remains non-null after {@link #close()}. */
    public Path path() {
        return path;
    }

    /**
     * Deletes the file best-effort. Safe to call more than once.
     *
     * <p>Any {@link IOException} from the delete is logged at WARN level
     * and swallowed — {@code close()} never throws.</p>
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
