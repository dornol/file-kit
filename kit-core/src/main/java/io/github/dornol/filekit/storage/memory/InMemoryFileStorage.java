package io.github.dornol.filekit.storage.memory;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileUploadCommand;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link FileStorage} implementation for testing and prototyping.
 *
 * <p>Stores file content in a {@link ConcurrentHashMap}. All data is lost
 * when the JVM shuts down.</p>
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

    public InMemoryFileStorage(Enum<?> storageType) {
        this.storageType = storageType;
    }

    @Override
    public Enum<?> getStorageType() {
        return storageType;
    }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = command.bucket() + "/" + command.key() + "." + command.extension();
        store.put(objectKey, command.content().clone());
        return new FileLocation(command.bucket(), command.key() + "." + command.extension(), storageType);
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        String objectKey = metadata.location().bucket() + "/" + metadata.location().objectKey();
        byte[] data = store.get(objectKey);
        if (data == null) {
            throw new IllegalArgumentException("File not found in memory store: " + objectKey);
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

}
