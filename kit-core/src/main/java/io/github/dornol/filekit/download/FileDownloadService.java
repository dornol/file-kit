package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.io.ChecksumVerifyingInputStream;
import io.github.dornol.filekit.io.IoUtils;
import io.github.dornol.filekit.io.DecryptionHelper;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.storage.AbstractFileOperationService;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.jspecify.annotations.Nullable;
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
    private final @Nullable ChecksumCalculator checksumCalculator;
    private final @Nullable Duration maxPresignedExpiration;

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

    private FileDownloadService(Builder b) {
        super(b.metadataRepository, b.storageResolver);
        this.fileEncryptor = Objects.requireNonNull(b.fileEncryptor, "fileEncryptor");
        this.eventPublisher = Objects.requireNonNull(b.eventPublisher, "eventPublisher");
        this.checksumCalculator = b.checksumCalculator;
        this.maxPresignedExpiration = b.maxPresignedExpiration;
    }

    public static final class Builder {

        private final FileMetadataRepository metadataRepository;
        private final FileStorageResolver storageResolver;
        private FileEncryptor fileEncryptor = new NoOpFileEncryptor();
        private FileEventPublisher eventPublisher = new FileEventPublisher(List.of());
        private @Nullable ChecksumCalculator checksumCalculator;
        private @Nullable Duration maxPresignedExpiration;

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

        /**
         * Enables download integrity verification.
         *
         * <p>When set, {@link #download(String)} verifies the downloaded content's
         * checksum against the stored checksum. Throws {@link FileStorageException}
         * with {@link FileStorageException#CHECKSUM_MISMATCH} if they differ.</p>
         *
         * @param checksumCalculator the calculator to use for verification
         */
        public Builder checksumCalculator(ChecksumCalculator checksumCalculator) {
            this.checksumCalculator = checksumCalculator;
            return this;
        }

        /**
         * Sets the maximum allowed expiration for pre-signed URLs.
         *
         * <p>If a caller requests a longer expiration in
         * {@link FileDownloadService#generatePresignedUrl(String, Duration)},
         * a {@link FileStorageException} is thrown.</p>
         *
         * @param maxPresignedExpiration maximum expiration duration
         */
        public Builder maxPresignedExpiration(Duration maxPresignedExpiration) {
            if (maxPresignedExpiration.isNegative() || maxPresignedExpiration.isZero()) {
                throw new IllegalArgumentException("maxPresignedExpiration must be positive");
            }
            this.maxPresignedExpiration = maxPresignedExpiration;
            return this;
        }

        public FileDownloadService build() {
            return new FileDownloadService(this);
        }
    }

    /**
     * Downloads a file by key.
     *
     * <p>If a {@link ChecksumCalculator} was configured via
     * {@link Builder#checksumCalculator(ChecksumCalculator)}, the returned
     * {@link InputStream} transparently verifies the checksum while being read.
     * Verification completes when the consumer reads to EOF; if the stored
     * checksum does not match the actual content, a
     * {@link FileStorageException} with
     * {@link FileStorageException#CHECKSUM_MISMATCH} is thrown from the
     * {@code read()} call that would otherwise return {@code -1}.
     *
     * <p>Closing the returned stream before EOF skips verification (a warning
     * is logged) — this accommodates partial reads such as preview or range
     * transfers. Callers that require strict end-to-end verification must
     * consume the stream to completion.</p>
     *
     * <p>Memory footprint is O(buffer), independent of file size.</p>
     */
    public DownloadResult download(String fileKey) {
        Objects.requireNonNull(fileKey, "fileKey");
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        InputStream content = resolveStorage(metadata).load(metadata);

        try {
            if (fileEncryptor.isEnabled()) {
                content = decryptToStream(content);
            }

            if (checksumCalculator != null) {
                content = verifyChecksum(content, metadata);
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

    /**
     * Generates a pre-signed URL for direct file access.
     *
     * <p>If {@link Builder#maxPresignedExpiration(Duration)} was configured,
     * the requested expiration is validated against it.</p>
     *
     * @throws FileStorageException if the requested expiration exceeds the configured maximum
     */
    public String generatePresignedUrl(String fileKey, Duration expiration) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(expiration, "expiration");
        if (maxPresignedExpiration != null && expiration.compareTo(maxPresignedExpiration) > 0) {
            throw new FileStorageException(FileStorageException.PRESIGNED_URL_FAILED,
                    "Requested expiration " + expiration + " exceeds maximum " + maxPresignedExpiration);
        }
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        return resolveStorage(metadata).generatePresignedUrl(metadata, expiration);
    }

    private InputStream verifyChecksum(InputStream content, FileMetadata metadata) {
        return new ChecksumVerifyingInputStream(
                content,
                checksumCalculator.newComputation(),
                metadata.checksum(),
                metadata.key()
        );
    }

    private InputStream decryptToStream(InputStream encryptedContent) {
        return DecryptionHelper.decryptToStream(encryptedContent, fileEncryptor);
    }

}
