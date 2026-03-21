package io.github.dornol.filekit.io;

import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.storage.FileStorageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decrypts an encrypted input stream to a temporary file and returns
 * a {@link DeleteOnCloseInputStream} that cleans up the temp file on close.
 *
 * <p>Shared by {@code FileDownloadService} and {@code SpringDownloadService}
 * to avoid duplicating decryption-to-stream logic.</p>
 */
public final class DecryptionHelper {

    private DecryptionHelper() {}

    /**
     * Decrypts the given encrypted stream using the provided encryptor.
     *
     * <p>The encrypted input stream is fully consumed and closed. The returned
     * stream reads from a decrypted temp file that is automatically deleted
     * when the stream is closed.</p>
     *
     * @param encryptedContent the encrypted input stream (will be closed)
     * @param fileEncryptor    the encryptor to use for decryption
     * @return an input stream of the decrypted content
     * @throws FileStorageException if decryption fails
     */
    public static InputStream decryptToStream(InputStream encryptedContent, FileEncryptor fileEncryptor) {
        Path decryptedFile = null;
        try {
            decryptedFile = Files.createTempFile("file-kit-decrypted-", ".tmp");
            try (InputStream in = encryptedContent;
                 OutputStream out = Files.newOutputStream(decryptedFile)) {
                fileEncryptor.decrypt(in, out);
            }
            return new DeleteOnCloseInputStream(decryptedFile);
        } catch (IOException e) {
            if (decryptedFile != null) {
                try {
                    Files.deleteIfExists(decryptedFile);
                } catch (IOException deleteEx) {
                    e.addSuppressed(deleteEx);
                }
            }
            throw new FileStorageException(FileStorageException.DECRYPTION_FAILED,
                    "Failed to decrypt file content", e);
        }
    }

}
