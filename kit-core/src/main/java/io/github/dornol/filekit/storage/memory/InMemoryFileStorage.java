package io.github.dornol.filekit.storage.memory;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileUploadCommand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link FileStorage} implementation for testing and prototyping.
 *
 * <p><strong>Warning:</strong> This implementation is NOT suitable for production use.
 * All data is lost when the JVM shuts down, and there is no size limit on stored files,
 * which can lead to {@link OutOfMemoryError} under heavy load.</p>
 *
 * <p>Stores file content in a {@link ConcurrentHashMap}.</p>
 *
 * <pre>{@code
 * // In tests
 * FileStorage storage = new InMemoryFileStorage(StorageType.LOCAL);
 *
 * // Or as a Spring bean for prototyping
 * @Bean
 * public FileStorage fileStorage() {
 *     return new InMemoryFileStorage(StorageType.LOCAL);
 * }
 * }</pre>
 */
public class InMemoryFileStorage implements FileStorage {

    private final Enum<?> storageType;
    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    /** @param storageType enum constant identifying this storage backend */
    public InMemoryFileStorage(Enum<?> storageType) {
        this.storageType = storageType;
    }

    @Override
    public Enum<?> getStorageType() {
        return storageType;
    }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = command.key() + "." + command.extension();
        try {
            store.put(buildStoreKey(command.bucket(), objectKey), command.content().readAllBytes());
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "Failed to read upload content");
        }
        return new FileLocation(command.bucket(), objectKey, storageType);
    }

    @Override
    public void delete(FileMetadata metadata) {
        store.remove(buildStoreKey(metadata));
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        String storeKey = buildStoreKey(metadata);
        byte[] data = store.get(storeKey);
        if (data == null) {
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "File not found in memory store: " + storeKey);
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return "memory://" + metadata.location().bucket() + "/" + metadata.location().objectKey();
    }

    /**
     * Returns the number of files currently stored.
     */
    public int size() {
        return store.size();
    }

    /**
     * Removes all stored files.
     */
    public void clear() {
        store.clear();
    }

    private static String buildStoreKey(FileMetadata metadata) {
        return buildStoreKey(metadata.location().bucket(), metadata.location().objectKey());
    }

    private static String buildStoreKey(String bucket, String objectKey) {
        return bucket + "/" + objectKey;
    }

}
