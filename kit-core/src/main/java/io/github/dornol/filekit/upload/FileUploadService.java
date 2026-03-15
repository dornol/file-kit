package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Orchestrates the file upload flow: checksum deduplication, format detection,
 * storage delegation, and metadata persistence.
 *
 * @see FileStorage
 * @see FileMetadataRepository
 */
public class FileUploadService {

    private final ChecksumCalculator checksumCalculator;
    private final FileMetadataRepository metadataRepository;
    private final FileFormatExtractor formatExtractor;
    private final FileStorageResolver storageResolver;

    public FileUploadService(ChecksumCalculator checksumCalculator,
                             FileMetadataRepository metadataRepository,
                             FileFormatExtractor formatExtractor,
                             FileStorageResolver storageResolver) {
        this.checksumCalculator = checksumCalculator;
        this.metadataRepository = metadataRepository;
        this.formatExtractor = formatExtractor;
        this.storageResolver = storageResolver;
    }

    /**
     * Uploads a file: dedup check, format detection, storage, metadata save.
     */
    public FileMetadata upload(FileSource fileSource, Enum<?> storageType, String bucket) throws IOException {
        return doUpload(fileSource, storageType, bucket, null);
    }

    /**
     * Uploads a file and runs a callback before persisting metadata.
     *
     * <p>If the callback throws, the uploaded file is deleted from storage
     * and the exception is wrapped in a {@link RuntimeException} (or re-thrown as-is
     * if it is already unchecked).</p>
     *
     * @param fileSource  the file to upload
     * @param storageType storage backend to use
     * @param bucket      target bucket
     * @param callback    business logic to run after upload, before metadata save
     */
    public FileMetadata upload(FileSource fileSource, Enum<?> storageType, String bucket,
                               UploadCallback callback) throws IOException {
        return doUpload(fileSource, storageType, bucket, callback);
    }

    private FileMetadata doUpload(FileSource fileSource, Enum<?> storageType, String bucket,
                                  @Nullable UploadCallback callback) throws IOException {
        byte[] bytes = fileSource.getInputStream().readAllBytes();

        String checksum = checksumCalculator.checksum(bytes);
        FileMetadata existing = metadataRepository.findByChecksum(checksum);
        if (existing != null) {
            return existing;
        }

        FileFormat format = formatExtractor.extract(new ByteArrayInputStream(bytes));

        String key = UUID.randomUUID().toString();
        FileUploadCommand command = new FileUploadCommand(
                key,
                fileSource.getOriginalFilename(),
                bytes,
                format.mimeType(),
                format.extension(),
                bucket
        );

        FileStorage storage = storageResolver.resolve(storageType);
        FileLocation location = storage.upload(command);

        FileMetadata metadata = new FileMetadata(
                key,
                fileSource.getOriginalFilename(),
                bytes.length,
                checksum,
                format,
                location
        );

        if (callback != null) {
            try {
                callback.onUploaded(metadata);
            } catch (Exception e) {
                storage.delete(metadata);
                throw new FileStorageException(FileStorageException.CALLBACK_FAILED,
                        "Upload callback failed, file has been deleted: " + metadata.key(), e);
            }
        }

        return metadataRepository.save(metadata);
    }

}
