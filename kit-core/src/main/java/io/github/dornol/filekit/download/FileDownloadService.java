package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.io.IoUtils;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.storage.AbstractFileOperationService;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
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

    /**
     * Creates a builder with the two required dependencies.
     *
     * @param metadataRepository repository for file metadata lookup
     * @param storageResolver    resolver to find storage backends
     * @return a new builder instance
     */
    public static Builder builder(FileMetadataRepository metadataRepository,
                                  FileStorageResolver storageResolver) {
        return new Builder(metadataRepository, storageResolver);
    }

    /** @deprecated Use {@link #builder(FileMetadataRepository, FileStorageResolver)} instead. */
    @Deprecated(forRemoval = true)
    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver) {
        this(metadataRepository, storageResolver, new NoOpFileEncryptor(), new FileEventPublisher(List.of()));
    }

    /** @deprecated Use {@link #builder(FileMetadataRepository, FileStorageResolver)} instead. */
    @Deprecated(forRemoval = true)
    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver,
                               FileEncryptor fileEncryptor) {
        this(metadataRepository, storageResolver, fileEncryptor, new FileEventPublisher(List.of()));
    }

    /** @deprecated Use {@link #builder(FileMetadataRepository, FileStorageResolver)} instead. */
    @Deprecated(forRemoval = true)
    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver,
                               FileEncryptor fileEncryptor,
                               FileEventPublisher eventPublisher) {
        super(metadataRepository, storageResolver);
        this.fileEncryptor = Objects.requireNonNull(fileEncryptor, "fileEncryptor");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    private FileDownloadService(Builder b) {
        super(b.metadataRepository, b.storageResolver);
        this.fileEncryptor = Objects.requireNonNull(b.fileEncryptor, "fileEncryptor");
        this.eventPublisher = Objects.requireNonNull(b.eventPublisher, "eventPublisher");
    }

    public static final class Builder {

        private final FileMetadataRepository metadataRepository;
        private final FileStorageResolver storageResolver;
        private FileEncryptor fileEncryptor = new NoOpFileEncryptor();
        private FileEventPublisher eventPublisher = new FileEventPublisher(List.of());

        private Builder(FileMetadataRepository metadataRepository,
                        FileStorageResolver storageResolver) {
            this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
            this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        }

        /** @param fileEncryptor encryptor for at-rest decryption */
        public Builder fileEncryptor(FileEncryptor fileEncryptor) {
            this.fileEncryptor = fileEncryptor;
            return this;
        }

        /** @param eventPublisher publisher for file lifecycle events */
        public Builder eventPublisher(FileEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public FileDownloadService build() {
            return new FileDownloadService(this);
        }
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
        return io.github.dornol.filekit.io.DecryptionHelper.decryptToStream(encryptedContent, fileEncryptor);
    }

}
