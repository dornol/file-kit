package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.io.DeleteOnCloseInputStream;
import io.github.dornol.filekit.io.IoUtils;
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
import java.util.List;
import java.util.Objects;

/**
 * Provides file download and URI resolution by file key.
 *
 * @see FileMetadataRepository
 */
public class FileDownloadService extends AbstractFileOperationService {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadService.class);

    private final FileEncryptor fileEncryptor;
    private final FileEventPublisher eventPublisher;

    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver) {
        this(metadataRepository, storageResolver, new NoOpFileEncryptor(), new FileEventPublisher(List.of()));
    }

    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver,
                               FileEncryptor fileEncryptor) {
        this(metadataRepository, storageResolver, fileEncryptor, new FileEventPublisher(List.of()));
    }

    /**
     * Creates a download service with the specified encryptor and event publisher.
     *
     * @param metadataRepository repository for file metadata lookup
     * @param storageResolver    resolver to find storage backends
     * @param fileEncryptor      encryptor for at-rest decryption
     * @param eventPublisher     publisher for file lifecycle events
     */
    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver,
                               FileEncryptor fileEncryptor,
                               FileEventPublisher eventPublisher) {
        super(metadataRepository, storageResolver);
        this.fileEncryptor = Objects.requireNonNull(fileEncryptor, "fileEncryptor");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public DownloadResult download(String fileKey) {
        Objects.requireNonNull(fileKey, "fileKey");
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        InputStream content = resolveStorage(metadata).load(metadata);

        try {
            if (fileEncryptor.isEnabled()) {
                content = decryptToStream(content);
            }

            log.info("File downloaded: key={}", fileKey);
            eventPublisher.fireDownloaded(metadata);
            return new DownloadResult(metadata, content);
        } catch (Exception e) {
            IoUtils.closeQuietly(content);
            throw e;
        }
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
