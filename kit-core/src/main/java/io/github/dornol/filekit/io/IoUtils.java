package io.github.dornol.filekit.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility methods for I/O operations.
 */
public final class IoUtils {

    private IoUtils() {}

    /**
     * Closes the given stream, swallowing any {@link IOException}.
     *
     * @param stream the stream to close (may be {@code null})
     */
    public static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
