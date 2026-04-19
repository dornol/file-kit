package io.github.dornol.filekit.validator;

import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

/**
 * Default {@link MediaTypeDetector} implementation. Uses a layered strategy:
 *
 * <ol>
 *   <li>Magic-byte sniffing via {@link MagicByteMatcher} — covers PDF, ZIP,
 *       PNG, JPEG, GIF, BMP, WebP, MP4, OGG, Zstandard.</li>
 *   <li>JDK {@link URLConnection#guessContentTypeFromStream(InputStream)} —
 *       catches anything the magic-byte table misses (notably some text
 *       subtypes).</li>
 *   <li>JDK {@link URLConnection#guessContentTypeFromName(String)} — filename
 *       extension fallback.</li>
 *   <li>{@code application/octet-stream} when all probes return {@code null}.</li>
 * </ol>
 *
 * <p>This detector requires no external dependencies. For broader coverage
 * (Office formats, container-aware detection, charset sniffing), plug in an
 * Apache Tika-based {@link MediaTypeDetector}.</p>
 *
 * <p>Since 0.1.22 the magic-byte pass runs before the JDK stream probe,
 * which changes the observable result for inputs the JDK previously
 * returned {@code application/octet-stream} for (now they get their actual
 * MIME). Validators with allow-lists may need to include the newly-detected
 * types.</p>
 */
public class DefaultMediaTypeDetector implements MediaTypeDetector {

    private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

    @Override
    public String detect(@Nullable String filename, @Nullable InputStream inputStream) throws IOException {
        if (inputStream != null) {
            InputStream buffered = inputStream.markSupported()
                    ? inputStream
                    : new BufferedInputStream(inputStream);

            // (1) Magic-byte sniff
            byte[] header = new byte[MagicByteMatcher.MAX_HEADER_BYTES];
            buffered.mark(MagicByteMatcher.MAX_HEADER_BYTES);
            int read = buffered.readNBytes(header, 0, header.length);
            buffered.reset();
            if (read > 0) {
                String magicDetected = MagicByteMatcher.match(header, read);
                if (magicDetected != null) {
                    return magicDetected;
                }
            }

            // (2) JDK stream probe
            String streamDetected = URLConnection.guessContentTypeFromStream(buffered);
            if (streamDetected != null) {
                return streamDetected;
            }
        }

        // (3) Filename-based fallback
        if (filename != null) {
            String byName = URLConnection.guessContentTypeFromName(filename);
            if (byName != null) {
                return byName;
            }
        }

        // (4) Ultimate fallback
        return DEFAULT_MEDIA_TYPE;
    }
}
