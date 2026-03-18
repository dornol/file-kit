package io.github.dornol.filekit.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Default {@link FileEncryptor} that performs no encryption.
 * Content is passed through unchanged.
 */
public class NoOpFileEncryptor implements FileEncryptor {

    /**
     * Copies content from {@code plainInput} to {@code cipherOutput} without any transformation.
     *
     * {@inheritDoc}
     */
    @Override
    public void encrypt(InputStream plainInput, OutputStream cipherOutput) throws IOException {
        plainInput.transferTo(cipherOutput);
    }

    /**
     * Copies content from {@code cipherInput} to {@code plainOutput} without any transformation.
     *
     * {@inheritDoc}
     */
    @Override
    public void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException {
        cipherInput.transferTo(plainOutput);
    }
}
