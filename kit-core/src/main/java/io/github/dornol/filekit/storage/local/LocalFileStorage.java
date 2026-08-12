package io.github.dornol.filekit.storage.local;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileUploadCommand;
import io.github.dornol.filekit.storage.StorageHealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@link FileStorage} implementation that stores files on the local filesystem.
 *
 * <p>Files are organized under {@code baseDir/bucket/objectKey} where the
 * object key is determined by the configured {@link ObjectKeyStrategy}.</p>
 *
 * <p>This class is not registered as a bean by default.
 * Users should instantiate and register it themselves:</p>
 * <pre>{@code
 * @Bean
 * public FileStorage localStorage() {
 *     return new LocalFileStorage(
 *             Path.of("/data/files"),
 *             StorageType.LOCAL,
 *             ObjectKeyStrategy.hashPrefixed(2));
 * }
 * }</pre>
 */
public class LocalFileStorage implements FileStorage, StorageHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path baseDir;
    private final Enum<?> storageType;
    private final ObjectKeyStrategy keyStrategy;

    /**
     * Creates a local file storage with the {@link ObjectKeyStrategy#flat() flat} strategy.
     *
     * @param baseDir     root directory for file storage
     * @param storageType enum constant identifying this storage
     */
    public LocalFileStorage(Path baseDir, Enum<?> storageType) {
        this(baseDir, storageType, ObjectKeyStrategy.flat());
    }

    /**
     * Creates a local file storage with a custom key strategy.
     *
     * @param baseDir     root directory for file storage
     * @param storageType enum constant identifying this storage
     * @param keyStrategy strategy for computing object keys
     */
    public LocalFileStorage(Path baseDir, Enum<?> storageType, ObjectKeyStrategy keyStrategy) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.storageType = storageType;
        this.keyStrategy = keyStrategy;
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create base directory", e);
        }
    }

    @Override
    public Enum<?> getStorageType() {
        return storageType;
    }

    @Override
    public void check() {
        if (!Files.isDirectory(baseDir) || !Files.isReadable(baseDir) || !Files.isWritable(baseDir)) {
            throw new IllegalStateException("Local storage directory is unavailable: " + baseDir);
        }
    }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = keyStrategy.resolve(command.key(), command.extension());
        Path target = validatePath(baseDir.resolve(command.bucket()).resolve(objectKey));
        try {
            Files.createDirectories(target.getParent());
            validateExistingParentPath(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.copy(command.content(), tmp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    log.debug("ATOMIC_MOVE not supported, falling back to REPLACE_EXISTING");
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException deleteEx) {
                    e.addSuppressed(deleteEx);
                }
                throw e;
            }
        } catch (IOException e) {
            log.error("Failed to write file: {}", target, e);
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "Failed to write file", e);
        }
        return new FileLocation(command.bucket(), objectKey, storageType);
    }

    @Override
    public void delete(FileMetadata metadata) {
        Path filePath = resolveFilePath(metadata);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
            throw new FileStorageException(FileStorageException.DELETE_FAILED,
                    "Failed to delete file", e);
        }
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        Path filePath = resolveFilePath(metadata);
        validateExistingPath(filePath);
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            log.error("Failed to read file: {}", filePath, e);
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "Failed to read file", e);
        }
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return resolveFilePath(metadata).toUri().toString();
    }

    private Path resolveFilePath(FileMetadata metadata) {
        return validatePath(baseDir
                .resolve(metadata.location().bucket())
                .resolve(metadata.location().objectKey()));
    }

    /**
     * Validates that the resolved path is within baseDir after normalization.
     */
    private Path validatePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(baseDir)) {
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "Path traversal detected");
        }
        return normalized;
    }

    /**
     * Validates that an existing file's real path (resolving symlinks) is within baseDir.
     *
     * @throws FileStorageException if the file does not exist or the resolved path escapes baseDir
     */
    private void validateExistingPath(Path path) {
        if (!Files.exists(path)) {
            throw new FileStorageException(FileStorageException.FILE_NOT_FOUND,
                    "File not found: " + path.getFileName());
        }
        try {
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(baseDir.toRealPath())) {
                throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                        "Path traversal detected");
            }
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "Failed to resolve file path", e);
        }
    }

    private void validateExistingParentPath(Path path) {
        try {
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(baseDir.toRealPath())) {
                throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                        "Path traversal detected");
            }
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "Failed to resolve upload path", e);
        }
    }

}
