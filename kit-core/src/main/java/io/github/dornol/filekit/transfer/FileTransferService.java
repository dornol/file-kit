package io.github.dornol.filekit.transfer;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.quota.QuotaChecker;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.AbstractFileOperationService;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Provides file copy and move operations between storage backends.
 */
public class FileTransferService extends AbstractFileOperationService {

    private static final Logger log = LoggerFactory.getLogger(FileTransferService.class);

    private final @Nullable QuotaChecker quotaChecker;
    private final FileEventPublisher eventPublisher;

    /**
     * Creates a builder with the two required dependencies.
     *
     * @param metadataRepository repository for file metadata lookup and persistence
     * @param storageResolver    resolver to find storage backends by type
     * @return a new builder instance
     */
    public static Builder builder(FileMetadataRepository metadataRepository,
                                  FileStorageResolver storageResolver) {
        return new Builder(metadataRepository, storageResolver);
    }

    private FileTransferService(Builder b) {
        super(b.metadataRepository, b.storageResolver);
        this.quotaChecker = b.quotaChecker;
        this.eventPublisher = Objects.requireNonNull(b.eventPublisher, "eventPublisher");
    }

    public static final class Builder {

        private final FileMetadataRepository metadataRepository;
        private final FileStorageResolver storageResolver;
        private @Nullable QuotaChecker quotaChecker;
        private FileEventPublisher eventPublisher = new FileEventPublisher(List.of());

        private Builder(FileMetadataRepository metadataRepository,
                        FileStorageResolver storageResolver) {
            this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
            this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        }

        /** @param quotaChecker optional quota checker for target bucket */
        public Builder quotaChecker(@Nullable QuotaChecker quotaChecker) {
            this.quotaChecker = quotaChecker;
            return this;
        }

        /** @param eventPublisher publisher for file lifecycle events */
        public Builder eventPublisher(FileEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public FileTransferService build() {
            return new FileTransferService(this);
        }
    }

    /**
     * Copies a file to a target storage backend and bucket.
     *
     * <p>Creates a new file with a new UUID key while preserving the original
     * filename, checksum, and format.</p>
     *
     * @param fileKey           key of the source file
     * @param targetStorageType target storage backend
     * @param targetBucket      target bucket name
     * @return metadata of the copied file
     */
    public FileMetadata copy(String fileKey, Enum<?> targetStorageType, String targetBucket) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(targetStorageType, "targetStorageType");
        Objects.requireNonNull(targetBucket, "targetBucket");

        FileMetadata source = metadataRepository.getByKey(fileKey);
        FileMetadata copied = doCopy(source, targetStorageType, targetBucket);
        log.info("File copied: sourceKey={}, newKey={}, targetBucket={}",
                fileKey, copied.key(), targetBucket);
        eventPublisher.fireCopied(source, copied);
        return copied;
    }

    /**
     * Moves a file to a target storage backend and bucket.
     *
     * <p>Copies the file to the target, then deletes the source file and its metadata.</p>
     *
     * <p><strong>Partial failure:</strong> If the copy succeeds but source deletion fails,
     * a {@link FileStorageException} with code {@link FileStorageException#MOVE_FAILED}
     * is thrown. In this case the copied file and its metadata already exist in the target.
     * The exception message contains the source file key; the caller should inspect the
     * target bucket to find and clean up the orphaned copy if needed.</p>
     *
     * @param fileKey           key of the source file
     * @param targetStorageType target storage backend
     * @param targetBucket      target bucket name
     * @return metadata of the moved file
     */
    public FileMetadata move(String fileKey, Enum<?> targetStorageType, String targetBucket) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(targetStorageType, "targetStorageType");
        Objects.requireNonNull(targetBucket, "targetBucket");

        FileMetadata source = metadataRepository.getByKey(fileKey);
        FileMetadata copied = doCopy(source, targetStorageType, targetBucket);

        try {
            metadataRepository.deleteByKey(fileKey);
            resolveStorage(source).delete(source);
            log.info("File moved: sourceKey={}, newKey={}", fileKey, copied.key());
        } catch (Exception e) {
            log.warn("Source deletion failed after copy (newKey={}): {}", copied.key(), e.getMessage());
            throw new FileStorageException(FileStorageException.MOVE_FAILED,
                    "File copied but source deletion failed: " + fileKey, e);
        }

        eventPublisher.fireMoved(source, copied);
        return copied;
    }

    private FileMetadata doCopy(FileMetadata source, Enum<?> targetStorageType, String targetBucket) {
        if (quotaChecker != null) {
            quotaChecker.check(targetStorageType, targetBucket, source.size());
        }

        FileStorage sourceStorage = resolveStorage(source);
        FileStorage targetStorage = storageResolver.resolve(targetStorageType);

        String newKey = UUID.randomUUID().toString();

        // Buffer to temp file to get the actual stored size (may differ from
        // metadata.size() when encryption is active).
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("file-kit-transfer-", ".tmp");
            try (InputStream content = sourceStorage.load(source)) {
                Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            long actualSize = Files.size(tempFile);

            try (InputStream buffered = Files.newInputStream(tempFile)) {
                FileUploadCommand command = new FileUploadCommand(
                        newKey,
                        source.name(),
                        buffered,
                        actualSize,
                        source.format().mimeType(),
                        source.format().extension(),
                        targetBucket
                );

                FileLocation newLocation = targetStorage.upload(command);
                FileMetadata copied = new FileMetadata(
                        newKey, source.name(), source.size(), source.checksum(),
                        source.format(), newLocation
                );

                return metadataRepository.save(copied);
            }
        } catch (FileStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.COPY_FAILED,
                    "Failed to copy file: " + source.key(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }
}
