package io.github.dornol.filekit.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An {@link InputStream} that deletes the backing temporary file when closed.
 *
 * <p>Used by download services to stream decrypted content from a temp file
 * while ensuring the file is cleaned up after the caller is done reading.</p>
 */
public class DeleteOnCloseInputStream extends InputStream {

    private final InputStream delegate;
    private final Path tempFile;

    /**
     * Creates a stream backed by the given temp file.
     * If opening the file fails, the temp file is deleted before the exception propagates.
     *
     * @param tempFile path to the temporary file
     * @throws IOException if the file cannot be opened
     */
    public DeleteOnCloseInputStream(Path tempFile) throws IOException {
        this.tempFile = tempFile;
        try {
            this.delegate = Files.newInputStream(tempFile);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        try {
            delegate.close();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
