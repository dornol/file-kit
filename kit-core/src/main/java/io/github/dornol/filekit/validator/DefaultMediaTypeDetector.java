package io.github.dornol.filekit.validator;

import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

/**
 * Default {@link MediaTypeDetector} implementation using Java's built-in
 * {@link URLConnection#guessContentTypeFromStream(InputStream)} and
 * {@link URLConnection#guessContentTypeFromName(String)}.
 *
 * <p>This detector requires no external dependencies but has limited
 * accuracy compared to Apache Tika. It is used as a fallback when
 * no other detector is available.</p>
 */
public class DefaultMediaTypeDetector implements MediaTypeDetector {

    private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

    @Override
    public String detect(@Nullable String filename, @Nullable InputStream inputStream) throws IOException {
        String detected = null;

        if (inputStream != null) {
            InputStream buffered = inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
            detected = URLConnection.guessContentTypeFromStream(buffered);
        }

        if (detected == null && filename != null) {
            detected = URLConnection.guessContentTypeFromName(filename);
        }

        return detected != null ? detected : DEFAULT_MEDIA_TYPE;
    }

}
