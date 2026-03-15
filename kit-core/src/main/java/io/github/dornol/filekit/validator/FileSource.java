package io.github.dornol.filekit.validator;

import java.io.IOException;
import java.io.InputStream;

public interface FileSource {

    String getOriginalFilename();

    InputStream getInputStream() throws IOException;

    long getSize();

    boolean isEmpty();

}
