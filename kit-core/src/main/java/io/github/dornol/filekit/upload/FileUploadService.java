package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.io.MagicByteBuffer;
import io.github.dornol.filekit.quota.QuotaChecker;
import io.github.dornol.filekit.scan.ScanResult;
import io.github.dornol.filekit.scan.VirusScanner;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.ChecksumComputation;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * To prevent this, add a <strong>unique constraint on the checksum column</strong>
 * in your {@link FileMetadataRepository} implementation and handle the
 * constraint violation (e.g. catch the exception and return the existing entry).
 * Without this, duplicate storage consumption and metadata bloat may occur
 * under high concurrency.</p>
 *
 * @see FileStorage
 * @see FileMetadataRepository
 */
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    static final String TEMP_UPLOAD_PREFIX = "file-kit-upload-";
    static final String TEMP_ENCRYPTED_PREFIX = "file-kit-encrypted-";

    private final ChecksumCalculator checksumCalculator;
    private final FileMetadataRepository metadataRepository;
    private final FileFormatExtractor formatExtractor;
    private final FileStorageResolver storageResolver;
    private final long maxUploadSize;
    private final @Nullable VirusScanner virusScanner;
    private final FileEncryptor fileEncryptor;
    private final @Nullable QuotaChecker quotaChecker;
    private final FileEventPublisher eventPublisher;
    private final int formatHeaderBufferSize;

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
        this.formatHeaderBufferSize = b.formatHeaderBufferSize;
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
        private int formatHeaderBufferSize = MagicByteBuffer.DEFAULT_SIZE;

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
            if (maxUploadSize < 0) {
                throw new IllegalArgumentException("maxUploadSize must not be negative: " + maxUploadSize);
            }
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

        /**
         * Sets the size of the header buffer used for format detection during ingest.
         * A larger buffer improves detection for formats that require a longer
         * magic-byte prefix (e.g. some XML wrappers) at the cost of per-upload
         * memory. Default: {@link MagicByteBuffer#DEFAULT_SIZE} (16 KiB).
         *
         * @throws IllegalArgumentException if {@code bytes < MagicByteBuffer.MIN_SIZE}
         * @since 0.1.12
         */
        public Builder formatHeaderBufferSize(int bytes) {
            if (bytes < MagicByteBuffer.MIN_SIZE) {
                throw new IllegalArgumentException(
                        "formatHeaderBufferSize must be at least "
                                + MagicByteBuffer.MIN_SIZE + ", got " + bytes);
            }
            this.formatHeaderBufferSize = bytes;
            return this;
        }

        public FileUploadService build() {
            return new FileUploadService(this);
        }
    }

    /**
     * Uploads a file: dedup check, format detection, storage, metadata save.
     *
     * <p><strong>Deduplication:</strong> If a file with the same checksum already exists,
     * the existing metadata is returned immediately without storing a new copy.
     * In this case, the {@code callback} (if provided) is <em>not</em> executed,
     * no upload event is fired, and the {@code storageType}/{@code bucket} parameters
     * are ignored (the original file's location is preserved).
     * If you need per-upload side effects regardless of deduplication,
     * check the returned metadata's key against your expected new key.</p>
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
     * <p><strong>Quota note:</strong> If the callback fails, the file is removed from
     * storage but the quota usage is <em>not</em> automatically rolled back.
     * If your {@link io.github.dornol.filekit.spi.QuotaUsageProvider} tracks usage
     * independently (e.g. via a database counter), you must compensate the usage
     * in your error-handling logic. For example, wrap the upload in a try-catch
     * and decrement the quota counter on {@link FileStorageException} with
     * {@link FileStorageException#CALLBACK_FAILED}.</p>
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

    /**
     * Uploads multiple files using a best-effort strategy.
     *
     * <p>Attempts to upload every file and collects results. Does not stop on first failure.</p>
     *
     * @param fileSources collection of files to upload
     * @param storageType storage backend to use
     * @param bucket      target bucket
     * @return result indicating which uploads succeeded and which failed
     */
    public BatchUploadResult uploadAll(Collection<? extends FileSource> fileSources,
                                       Enum<?> storageType, String bucket) {
        Objects.requireNonNull(fileSources, "fileSources");
        Objects.requireNonNull(storageType, "storageType");
        Objects.requireNonNull(bucket, "bucket");

        List<FileMetadata> succeeded = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();

        for (FileSource fileSource : fileSources) {
            String name = fileSource.getOriginalFilename() != null
                    ? fileSource.getOriginalFilename() : "(unnamed)";
            try {
                FileMetadata meta = upload(fileSource, storageType, bucket);
                succeeded.add(meta);
            } catch (Exception e) {
                log.warn("Failed to upload file: name={}", name, e);
                failed.put(name, e.getMessage());
            }
        }

        log.info("Batch upload completed: {} succeeded, {} failed out of {} requested",
                succeeded.size(), failed.size(), fileSources.size());
        return new BatchUploadResult(succeeded, failed);
    }

    private FileMetadata doUpload(FileSource fileSource, Enum<?> storageType, String bucket,
                                  @Nullable UploadCallback callback) throws IOException {
        Objects.requireNonNull(fileSource, "fileSource");
        Objects.requireNonNull(storageType, "storageType");
        Objects.requireNonNull(bucket, "bucket");

        validateFileSize(fileSource);
        validateFilename(fileSource.getOriginalFilename());

        Path tempFile = Files.createTempFile(TEMP_UPLOAD_PREFIX, ".tmp");
        Path encryptedFile = null;
        try {
            MagicByteBuffer header = new MagicByteBuffer(formatHeaderBufferSize);
            ChecksumComputation computation = checksumCalculator.newComputation();
            long bytesWritten = teeIngest(fileSource, tempFile, computation, header);
            String checksum = computation.finish();

            // Virus scan runs on every upload (including would-be duplicates) to
            // defend against signature-DB updates since a prior ingest.
            scanForVirus(tempFile);

            FileMetadata existing = metadataRepository.findByChecksum(checksum);
            if (existing != null) {
                log.info("Duplicate file detected (checksum={}), returning existing metadata: {}",
                        checksum, existing.key());
                return existing;
            }

            FileFormat format = formatExtractor.extract(header.asInputStream());

            String key = UUID.randomUUID().toString();
            String name = fileSource.getOriginalFilename() != null
                    ? fileSource.getOriginalFilename()
                    : key + "." + format.extension();

            // Encrypt content to a separate temp file
            encryptedFile = Files.createTempFile(TEMP_ENCRYPTED_PREFIX, ".tmp");
            encryptFile(tempFile, encryptedFile);
            long encryptedSize = Files.size(encryptedFile);

            // Quota check uses the encrypted size (actual storage consumption)
            if (quotaChecker != null) {
                quotaChecker.check(storageType, bucket, encryptedSize);
            }

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

    /**
     * Copies the source stream to {@code tempFile} while simultaneously updating
     * the incremental checksum computation and capturing the leading bytes into
     * {@code header} for later format detection.
     *
     * @return total bytes written
     */
    private static long teeIngest(FileSource fileSource, Path tempFile,
                                  ChecksumComputation computation,
                                  MagicByteBuffer header) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        try (InputStream is = fileSource.getInputStream();
             OutputStream out = Files.newOutputStream(tempFile)) {
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
                computation.update(buf, 0, n);
                header.observe(buf, 0, n);
                total += n;
            }
        }
        return total;
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
