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
import io.github.dornol.filekit.validator.FilenameValidator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates the file upload flow: checksum deduplication, format detection,
 * storage delegation, and metadata persistence.
 *
 * <p><strong>Thread safety / TOCTOU note:</strong> The checksum-based deduplication
 * ({@code findByChecksum → save}) is not atomic. Under concurrent uploads of the
 * same file, both threads may pass the dedup check and store the file twice.
 * If strict uniqueness is required, enforce a unique constraint on the checksum
 * column in your {@link FileMetadataRepository} implementation.</p>
 *
 * @see FileStorage
 * @see FileMetadataRepository
 */
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final ChecksumCalculator checksumCalculator;
    private final FileMetadataRepository metadataRepository;
    private final FileFormatExtractor formatExtractor;
    private final FileStorageResolver storageResolver;
    private final long maxUploadSize;

    /**
     * Creates an upload service with no file size limit.
     */
    public FileUploadService(ChecksumCalculator checksumCalculator,
                             FileMetadataRepository metadataRepository,
                             FileFormatExtractor formatExtractor,
                             FileStorageResolver storageResolver) {
        this(checksumCalculator, metadataRepository, formatExtractor, storageResolver, 0);
    }

    /**
     * Creates an upload service with a maximum upload size.
     *
     * @param maxUploadSize maximum file size in bytes (0 = unlimited)
     */
    public FileUploadService(ChecksumCalculator checksumCalculator,
                             FileMetadataRepository metadataRepository,
                             FileFormatExtractor formatExtractor,
                             FileStorageResolver storageResolver,
                             long maxUploadSize) {
        this.checksumCalculator = Objects.requireNonNull(checksumCalculator, "checksumCalculator");
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        this.formatExtractor = Objects.requireNonNull(formatExtractor, "formatExtractor");
        this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        this.maxUploadSize = maxUploadSize;
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
        Objects.requireNonNull(fileSource, "fileSource");
        Objects.requireNonNull(storageType, "storageType");
        Objects.requireNonNull(bucket, "bucket");

        validateFileSize(fileSource);
        validateFilename(fileSource.getOriginalFilename());

        byte[] bytes = fileSource.getInputStream().readAllBytes();

        FileMetadata existing = findDuplicate(bytes);
        if (existing != null) {
            return existing;
        }

        FileFormat format = formatExtractor.extract(new ByteArrayInputStream(bytes));
        String key = UUID.randomUUID().toString();
        String checksum = checksumCalculator.checksum(bytes);
        String name = fileSource.getOriginalFilename() != null
                ? fileSource.getOriginalFilename()
                : key + "." + format.extension();

        FileStorage storage = storageResolver.resolve(storageType);
        FileLocation location = storage.upload(new FileUploadCommand(
                key, fileSource.getOriginalFilename(), bytes,
                format.mimeType(), format.extension(), bucket));

        FileMetadata metadata = new FileMetadata(key, name, bytes.length, checksum, format, location);

        executeCallback(callback, metadata, storage);

        FileMetadata saved = metadataRepository.save(metadata);
        log.info("File uploaded: key={}, size={}, bucket={}, storageType={}", saved.key(), saved.size(), bucket, storageType);
        return saved;
    }

    private void validateFileSize(FileSource fileSource) {
        if (maxUploadSize > 0 && fileSource.getSize() > maxUploadSize) {
            throw new FileStorageException(FileStorageException.FILE_TOO_LARGE,
                    "File size " + fileSource.getSize() + " exceeds maximum allowed size " + maxUploadSize);
        }
    }

    private static void validateFilename(@Nullable String filename) {
        if (filename == null) {
            return;
        }
        if (filename.length() > FilenameValidator.MAX_FILENAME_LENGTH) {
            throw new FileStorageException(FileStorageException.INVALID_FILENAME,
                    "Filename exceeds " + FilenameValidator.MAX_FILENAME_LENGTH + " characters");
        }
        if (FilenameValidator.containsTraversalCharacters(filename)) {
            throw new FileStorageException(FileStorageException.INVALID_FILENAME,
                    "Filename contains illegal characters: " + filename);
        }
    }

    private @Nullable FileMetadata findDuplicate(byte[] bytes) {
        String checksum = checksumCalculator.checksum(bytes);
        FileMetadata existing = metadataRepository.findByChecksum(checksum);
        if (existing != null) {
            log.info("Duplicate file detected (checksum={}), returning existing metadata: {}", checksum, existing.key());
        }
        return existing;
    }

    private static void executeCallback(@Nullable UploadCallback callback,
                                        FileMetadata metadata, FileStorage storage) {
        if (callback == null) {
            return;
        }
        try {
            callback.onUploaded(metadata);
        } catch (Exception e) {
            storage.delete(metadata);
            throw new FileStorageException(FileStorageException.CALLBACK_FAILED,
                    "Upload callback failed, file has been deleted: " + metadata.key(), e);
        }
    }

}
