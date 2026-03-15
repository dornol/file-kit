package io.github.dornol.filekit.validator;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

public class DefaultMediaTypeDetector implements MediaTypeDetector {

    private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

    @Override
    public String detect(String filename, InputStream inputStream) throws IOException {
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
