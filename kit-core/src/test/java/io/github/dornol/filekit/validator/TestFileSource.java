package io.github.dornol.filekit.validator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Simple {@link FileSource} implementation for testing.
 */
class TestFileSource implements FileSource {

    private final String filename;
    private final byte[] content;

    TestFileSource(String filename, byte[] content) {
        this.filename = filename;
        this.content = content;
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

}
