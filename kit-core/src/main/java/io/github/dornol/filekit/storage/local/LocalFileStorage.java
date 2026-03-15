package io.github.dornol.filekit.storage.local;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileUploadCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        this.baseDir = baseDir;
        this.storageType = storageType;
        this.keyStrategy = keyStrategy;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create base directory: " + baseDir, e);
        }
    }

    @Override
    public Enum<?> getStorageType() {
        return storageType;
    }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = keyStrategy.resolve(command.key(), command.extension());
        Path target = baseDir.resolve(command.bucket()).resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, command.content());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file: " + target, e);
        }
        return new FileLocation(command.bucket(), objectKey, storageType);
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        Path filePath = baseDir
                .resolve(metadata.location().bucket())
                .resolve(metadata.location().objectKey());
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + filePath, e);
        }
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return baseDir
                .resolve(metadata.location().bucket())
                .resolve(metadata.location().objectKey())
                .toUri()
                .toString();
    }

}
