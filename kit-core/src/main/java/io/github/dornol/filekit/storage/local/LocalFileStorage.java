package io.github.dornol.filekit.storage.local;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileUploadCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class LocalFileStorage implements FileStorage {

    private static final Logger log = Logger.getLogger(LocalFileStorage.class.getName());

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
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = keyStrategy.resolve(command.key(), command.extension());
        Path target = validatePath(baseDir.resolve(command.bucket()).resolve(objectKey));
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, command.content());
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to write file: " + target, e);
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "Failed to write file");
        }
        return new FileLocation(command.bucket(), objectKey, storageType);
    }

    @Override
    public void delete(FileMetadata metadata) {
        Path filePath = resolveFilePath(metadata);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to delete file: " + filePath, e);
            throw new FileStorageException(FileStorageException.DELETE_FAILED,
                    "Failed to delete file");
        }
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        Path filePath = resolveFilePath(metadata);
        validateExistingPath(filePath);
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to read file: " + filePath, e);
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "Failed to read file");
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
     */
    private void validateExistingPath(Path path) {
        if (Files.exists(path)) {
            try {
                Path realPath = path.toRealPath();
                if (!realPath.startsWith(baseDir.toRealPath())) {
                    throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                            "Path traversal detected");
                }
            } catch (IOException e) {
                throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                        "Failed to resolve file path");
            }
        }
    }

}
