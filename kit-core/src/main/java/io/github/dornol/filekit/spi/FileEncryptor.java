package io.github.dornol.filekit.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * SPI for encrypting and decrypting file content at rest.
 *
 * <p>Implementations should use streaming APIs (e.g. {@link javax.crypto.CipherInputStream})
 * to avoid loading entire files into memory.</p>
 *
 * <p>The default implementation ({@link NoOpFileEncryptor}) performs no encryption.</p>
 */
public interface FileEncryptor {

    /**
     * Encrypts content from the input stream and writes to the output stream.
     *
     * @param plainInput   plaintext input
     * @param cipherOutput encrypted output
     * @throws IOException if an I/O or encryption error occurs
     */
    void encrypt(InputStream plainInput, OutputStream cipherOutput) throws IOException;

    /**
     * Decrypts content from the input stream and writes to the output stream.
     *
     * @param cipherInput encrypted input
     * @param plainOutput decrypted output
     * @throws IOException if an I/O or decryption error occurs
     */
    void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException;
}
