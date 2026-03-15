package io.github.dornol.filekit.validator;

import java.io.IOException;
import java.io.InputStream;

public interface MediaTypeDetector {

    String detect(String filename, InputStream inputStream) throws IOException;

}
