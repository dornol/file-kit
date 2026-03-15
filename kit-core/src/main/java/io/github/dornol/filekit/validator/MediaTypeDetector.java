package io.github.dornol.filekit.validator;

import java.io.IOException;
import java.io.InputStream;

/**
 * Strategy interface for detecting the MIME type of a file.
 *
 * <p>Implementations may use Apache Tika, Java's built-in {@code URLConnection},
 * or any other detection mechanism.</p>
 *
 * @see DefaultMediaTypeDetector
 * @see io.github.dornol.filekit.spring.validator.TikaMediaTypeDetector
 */
public interface MediaTypeDetector {

    /**
     * Detects the MIME type of a file from its name and content stream.
     *
     * @param filename    the original filename (may be {@code null})
     * @param inputStream the file content stream (may be {@code null})
     * @return the detected MIME type string (e.g. {@code "image/png"})
     * @throws IOException if reading the stream fails
     */
    String detect(String filename, InputStream inputStream) throws IOException;

}
