package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.MediaTypeDetector;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link MediaTypeDetector} implementation using Apache Tika for accurate MIME type detection.
 *
 * <p>Auto-registered by {@link io.github.dornol.filekit.spring.autoconfigure.FileKitAutoConfiguration}
 * when Apache Tika is present on the classpath.</p>
 */
public class TikaMediaTypeDetector implements MediaTypeDetector {

    private final Tika tika = new Tika();

    @Override
    public String detect(String filename, InputStream inputStream) throws IOException {
        return tika.detect(inputStream, filename);
    }

}
