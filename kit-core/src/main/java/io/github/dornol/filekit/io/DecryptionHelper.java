package io.github.dornol.filekit.io;

import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.storage.FileStorageException;
import org.jspecify.annotations.Nullable;

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
        return decryptToStream(encryptedContent, fileEncryptor, null);
    }

    /**
     * Same as {@link #decryptToStream(InputStream, FileEncryptor)} but creates
     * the temp file in the given {@code tempDirectory}. When {@code null}, the
     * system temp directory is used.
     *
     * @since 0.1.25
     */
    public static InputStream decryptToStream(InputStream encryptedContent,
                                              FileEncryptor fileEncryptor,
                                              @Nullable Path tempDirectory) {
        try (TempFileBuffer buf = TempFileBuffer.create(tempDirectory, "file-kit-decrypted-")) {
            try (InputStream in = encryptedContent;
                 OutputStream out = Files.newOutputStream(buf.path())) {
                fileEncryptor.decrypt(in, out);
            }
            return new DeleteOnCloseInputStream(buf.release());
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.DECRYPTION_FAILED,
                    "Failed to decrypt file content", e);
        }
    }

}
