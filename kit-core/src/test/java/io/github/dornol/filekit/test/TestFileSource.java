package io.github.dornol.filekit.test;

import io.github.dornol.filekit.domain.FileSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Simple {@link FileSource} implementation for testing.
 */
public class TestFileSource implements FileSource {

    private final String filename;
    private final byte[] content;

    public TestFileSource(String filename, byte[] content) {
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
