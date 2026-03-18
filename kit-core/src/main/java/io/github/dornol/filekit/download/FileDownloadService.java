package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.storage.AbstractFileOperationService;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Provides file download and URI resolution by file key.
 *
 * @see FileMetadataRepository
 */
public class FileDownloadService extends AbstractFileOperationService {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadService.class);

    private final FileEncryptor fileEncryptor;

    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver) {
        this(metadataRepository, storageResolver, new NoOpFileEncryptor());
    }

    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver,
                               FileEncryptor fileEncryptor) {
        super(metadataRepository, storageResolver);
        this.fileEncryptor = Objects.requireNonNull(fileEncryptor, "fileEncryptor");
    }

    public DownloadResult download(String fileKey) {
        Objects.requireNonNull(fileKey, "fileKey");
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        InputStream content = resolveStorage(metadata).load(metadata);

        if (!(fileEncryptor instanceof NoOpFileEncryptor)) {
            content = decryptToStream(content);
        }

        log.info("File downloaded: key={}", fileKey);
        return new DownloadResult(metadata, content);
    }

    public String resolveUri(String fileKey) {
        Objects.requireNonNull(fileKey, "fileKey");
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        return resolveStorage(metadata).resolveUri(metadata);
    }

    public String generatePresignedUrl(String fileKey, Duration expiration) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(expiration, "expiration");
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        return resolveStorage(metadata).generatePresignedUrl(metadata, expiration);
    }

    private InputStream decryptToStream(InputStream encryptedContent) {
        try {
            Path decryptedFile = Files.createTempFile("file-kit-decrypted-", ".tmp");
            try (InputStream in = encryptedContent;
                 OutputStream out = Files.newOutputStream(decryptedFile)) {
                fileEncryptor.decrypt(in, out);
            }
            return new DeleteOnCloseInputStream(decryptedFile);
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.DECRYPTION_FAILED,
                    "Failed to decrypt file content", e);
        }
    }

    /**
     * InputStream that deletes the backing temp file when closed.
     */
    private static class DeleteOnCloseInputStream extends InputStream {

        private final InputStream delegate;
        private final Path tempFile;

        DeleteOnCloseInputStream(Path tempFile) throws IOException {
            this.tempFile = tempFile;
            this.delegate = Files.newInputStream(tempFile);
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

}
