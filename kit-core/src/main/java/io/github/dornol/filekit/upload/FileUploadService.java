package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.scan.ScanResult;
import io.github.dornol.filekit.scan.VirusScanner;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates the file upload flow: checksum deduplication, format detection,
 * storage delegation, and metadata persistence.
 *
 * <p>File content is buffered to a temporary file on disk so that arbitrarily
 * large uploads can be processed without loading the entire content into memory.</p>
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
    private final @Nullable VirusScanner virusScanner;

    /**
     * Creates an upload service with no file size limit and no virus scanner.
     */
    public FileUploadService(ChecksumCalculator checksumCalculator,
                             FileMetadataRepository metadataRepository,
                             FileFormatExtractor formatExtractor,
                             FileStorageResolver storageResolver) {
        this(checksumCalculator, metadataRepository, formatExtractor, storageResolver, 0, null);
    }

    /**
     * Creates an upload service with a maximum upload size and no virus scanner.
     *
     * @param maxUploadSize maximum file size in bytes (0 = unlimited)
     */
    public FileUploadService(ChecksumCalculator checksumCalculator,
                             FileMetadataRepository metadataRepository,
                             FileFormatExtractor formatExtractor,
                             FileStorageResolver storageResolver,
                             long maxUploadSize) {
        this(checksumCalculator, metadataRepository, formatExtractor, storageResolver, maxUploadSize, null);
    }

    /**
     * Creates an upload service with a maximum upload size and optional virus scanner.
     *
     * @param maxUploadSize maximum file size in bytes (0 = unlimited)
     * @param virusScanner  optional virus scanner; if non-null, files are scanned before upload
     */
    public FileUploadService(ChecksumCalculator checksumCalculator,
                             FileMetadataRepository metadataRepository,
                             FileFormatExtractor formatExtractor,
                             FileStorageResolver storageResolver,
                             long maxUploadSize,
                             @Nullable VirusScanner virusScanner) {
        this.checksumCalculator = Objects.requireNonNull(checksumCalculator, "checksumCalculator");
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        this.formatExtractor = Objects.requireNonNull(formatExtractor, "formatExtractor");
        this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        this.maxUploadSize = maxUploadSize;
        this.virusScanner = virusScanner;
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

        Path tempFile = Files.createTempFile("file-kit-upload-", ".tmp");
        try {
            long bytesWritten;
            try (InputStream is = fileSource.getInputStream()) {
                bytesWritten = Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            scanForVirus(tempFile);

            String checksum;
            try (InputStream is = Files.newInputStream(tempFile)) {
                checksum = checksumCalculator.checksum(is);
            }

            FileMetadata existing = metadataRepository.findByChecksum(checksum);
            if (existing != null) {
                log.info("Duplicate file detected (checksum={}), returning existing metadata: {}", checksum, existing.key());
                return existing;
            }

            FileFormat format;
            try (InputStream is = Files.newInputStream(tempFile)) {
                format = formatExtractor.extract(is);
            }

            String key = UUID.randomUUID().toString();
            String name = fileSource.getOriginalFilename() != null
                    ? fileSource.getOriginalFilename()
                    : key + "." + format.extension();

            FileStorage storage = storageResolver.resolve(storageType);
            FileLocation location;
            try (InputStream is = Files.newInputStream(tempFile)) {
                location = storage.upload(new FileUploadCommand(
                        key, fileSource.getOriginalFilename(), is, bytesWritten,
                        format.mimeType(), format.extension(), bucket));
            }

            FileMetadata metadata = new FileMetadata(key, name, bytesWritten, checksum, format, location);

            executeCallback(callback, metadata, storage);

            FileMetadata saved = metadataRepository.save(metadata);
            log.info("File uploaded: key={}, size={}, bucket={}, storageType={}", saved.key(), saved.size(), bucket, storageType);
            return saved;
        } finally {
            Files.deleteIfExists(tempFile);
        }
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

    private void scanForVirus(Path tempFile) throws IOException {
        if (virusScanner == null) {
            return;
        }
        ScanResult result;
        try (InputStream is = Files.newInputStream(tempFile)) {
            result = virusScanner.scan(is);
        }
        switch (result.status()) {
            case CLEAN -> log.debug("Virus scan passed");
            case INFECTED -> {
                log.warn("Virus detected: {}", result.message());
                throw new FileStorageException(FileStorageException.VIRUS_DETECTED,
                        "Virus detected: " + result.message());
            }
            case ERROR -> {
                log.error("Virus scan error: {}", result.message());
                throw new FileStorageException(FileStorageException.VIRUS_SCAN_ERROR,
                        "Virus scan error: " + result.message());
            }
        }
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
