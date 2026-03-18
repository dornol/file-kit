package io.github.dornol.filekit.transfer;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.AbstractFileOperationService;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * Provides file copy and move operations between storage backends.
 */
public class FileTransferService extends AbstractFileOperationService {

    private static final Logger log = LoggerFactory.getLogger(FileTransferService.class);

    /**
     * @param metadataRepository repository for file metadata lookup and persistence
     * @param storageResolver    resolver to find storage backends by type
     */
    public FileTransferService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver) {
        super(metadataRepository, storageResolver);
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
        FileStorage sourceStorage = resolveStorage(source);
        FileStorage targetStorage = storageResolver.resolve(targetStorageType);

        String newKey = UUID.randomUUID().toString();

        try (InputStream content = sourceStorage.load(source)) {
            FileUploadCommand command = new FileUploadCommand(
                    newKey,
                    source.name(),
                    content,
                    source.size(),
                    source.format().mimeType(),
                    source.format().extension(),
                    targetBucket
            );

            FileLocation newLocation = targetStorage.upload(command);
            FileMetadata copied = new FileMetadata(
                    newKey, source.name(), source.size(), source.checksum(),
                    source.format(), newLocation
            );

            FileMetadata saved = metadataRepository.save(copied);
            log.info("File copied: sourceKey={}, newKey={}, targetBucket={}",
                    fileKey, newKey, targetBucket);
            return saved;
        } catch (FileStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.COPY_FAILED,
                    "Failed to copy file: " + fileKey, e);
        }
    }

    /**
     * Moves a file to a target storage backend and bucket.
     *
     * <p>Copies the file to the target, then deletes the source file and its metadata.</p>
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
        FileMetadata copied = copy(fileKey, targetStorageType, targetBucket);

        try {
            resolveStorage(source).delete(source);
            metadataRepository.deleteByKey(fileKey);
            log.info("File moved: sourceKey={}, newKey={}", fileKey, copied.key());
        } catch (Exception e) {
            log.warn("Source deletion failed after copy (newKey={}): {}", copied.key(), e.getMessage());
            throw new FileStorageException(FileStorageException.MOVE_FAILED,
                    "File copied but source deletion failed: " + fileKey, e);
        }

        return copied;
    }
}
