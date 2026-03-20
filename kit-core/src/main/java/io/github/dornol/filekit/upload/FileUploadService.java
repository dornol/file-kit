package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.quota.QuotaChecker;
import io.github.dornol.filekit.scan.ScanResult;
import io.github.dornol.filekit.scan.VirusScanner;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
    private final FileEncryptor fileEncryptor;
    private final @Nullable QuotaChecker quotaChecker;
    private final FileEventPublisher eventPublisher;

    /**
     * Creates a builder with the four required dependencies.
     *
     * @param checksumCalculator calculator for file checksums
     * @param metadataRepository repository for file metadata persistence
     * @param formatExtractor    extractor for detecting file format
     * @param storageResolver    resolver for mapping storage type to storage backend
     * @return a new builder instance
     */
    public static Builder builder(ChecksumCalculator checksumCalculator,
                                  FileMetadataRepository metadataRepository,
                                  FileFormatExtractor formatExtractor,
                                  FileStorageResolver storageResolver) {
        return new Builder(checksumCalculator, metadataRepository, formatExtractor, storageResolver);
    }

    private FileUploadService(Builder b) {
        this.checksumCalculator = Objects.requireNonNull(b.checksumCalculator, "checksumCalculator");
        this.metadataRepository = Objects.requireNonNull(b.metadataRepository, "metadataRepository");
        this.formatExtractor = Objects.requireNonNull(b.formatExtractor, "formatExtractor");
        this.storageResolver = Objects.requireNonNull(b.storageResolver, "storageResolver");
        this.maxUploadSize = b.maxUploadSize;
        this.virusScanner = b.virusScanner;
        this.fileEncryptor = Objects.requireNonNull(b.fileEncryptor, "fileEncryptor");
        this.quotaChecker = b.quotaChecker;
        this.eventPublisher = Objects.requireNonNull(b.eventPublisher, "eventPublisher");
    }

    public static final class Builder {

        private final ChecksumCalculator checksumCalculator;
        private final FileMetadataRepository metadataRepository;
        private final FileFormatExtractor formatExtractor;
        private final FileStorageResolver storageResolver;

        private long maxUploadSize;
        private @Nullable VirusScanner virusScanner;
        private FileEncryptor fileEncryptor = new NoOpFileEncryptor();
        private @Nullable QuotaChecker quotaChecker;
        private FileEventPublisher eventPublisher = new FileEventPublisher(List.of());

        private Builder(ChecksumCalculator checksumCalculator,
                        FileMetadataRepository metadataRepository,
                        FileFormatExtractor formatExtractor,
                        FileStorageResolver storageResolver) {
            this.checksumCalculator = checksumCalculator;
            this.metadataRepository = metadataRepository;
            this.formatExtractor = formatExtractor;
            this.storageResolver = storageResolver;
        }

        /** @param maxUploadSize maximum file size in bytes (0 = unlimited) */
        public Builder maxUploadSize(long maxUploadSize) {
            this.maxUploadSize = maxUploadSize;
            return this;
        }

        /** @param virusScanner optional virus scanner; files are scanned before upload */
        public Builder virusScanner(@Nullable VirusScanner virusScanner) {
            this.virusScanner = virusScanner;
            return this;
        }

        /** @param fileEncryptor encryptor for at-rest encryption */
        public Builder fileEncryptor(FileEncryptor fileEncryptor) {
            this.fileEncryptor = fileEncryptor;
            return this;
        }

        /** @param quotaChecker optional quota checker; quota is verified before upload */
        public Builder quotaChecker(@Nullable QuotaChecker quotaChecker) {
            this.quotaChecker = quotaChecker;
            return this;
        }

        /** @param eventPublisher publisher for file lifecycle events */
        public Builder eventPublisher(FileEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public FileUploadService build() {
            return new FileUploadService(this);
        }
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
        Path encryptedFile = null;
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

            if (quotaChecker != null) {
                quotaChecker.check(storageType, bucket, bytesWritten);
            }

            FileFormat format;
            try (InputStream is = Files.newInputStream(tempFile)) {
                format = formatExtractor.extract(is);
            }

            String key = UUID.randomUUID().toString();
            String name = fileSource.getOriginalFilename() != null
                    ? fileSource.getOriginalFilename()
                    : key + "." + format.extension();

            // Encrypt content to a separate temp file
            encryptedFile = Files.createTempFile("file-kit-encrypted-", ".tmp");
            encryptFile(tempFile, encryptedFile);
            long encryptedSize = Files.size(encryptedFile);

            FileStorage storage = storageResolver.resolve(storageType);
            FileLocation location;
            try (InputStream is = Files.newInputStream(encryptedFile)) {
                location = storage.upload(new FileUploadCommand(
                        key, fileSource.getOriginalFilename(), is, encryptedSize,
                        format.mimeType(), format.extension(), bucket));
            }

            FileMetadata metadata = new FileMetadata(key, name, bytesWritten, checksum, format, location);

            executeCallback(callback, metadata, storage);

            FileMetadata saved = metadataRepository.save(metadata);
            log.info("File uploaded: key={}, size={}, bucket={}, storageType={}", saved.key(), saved.size(), bucket, storageType);
            eventPublisher.fireUploaded(saved);
            return saved;
        } finally {
            Files.deleteIfExists(tempFile);
            if (encryptedFile != null) {
                Files.deleteIfExists(encryptedFile);
            }
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

    private void encryptFile(Path plainFile, Path encryptedFile) {
        try (InputStream in = Files.newInputStream(plainFile);
             OutputStream out = Files.newOutputStream(encryptedFile)) {
            fileEncryptor.encrypt(in, out);
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.ENCRYPTION_FAILED,
                    "Failed to encrypt file content", e);
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
