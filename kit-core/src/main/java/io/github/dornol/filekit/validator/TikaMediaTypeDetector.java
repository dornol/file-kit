package io.github.dornol.filekit.validator;

import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;

public class TikaMediaTypeDetector implements MediaTypeDetector {

    private final Tika tika = new Tika();

    @Override
    public String detect(String filename, InputStream inputStream) throws IOException {
        return tika.detect(inputStream, filename);
    }

}
