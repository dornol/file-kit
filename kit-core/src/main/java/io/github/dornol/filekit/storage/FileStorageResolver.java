package io.github.dornol.filekit.storage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves a {@link FileStorage} implementation by its {@link FileStorage#getStorageType() storageType}.
 *
 * <p>Constructed with a list of all available storages and builds
 * an internal lookup map for O(1) resolution.</p>
 */
public class FileStorageResolver {

    private final Map<Enum<?>, FileStorage> storageMap;

    public FileStorageResolver(List<FileStorage> storages) {
        this.storageMap = storages.stream()
                .collect(Collectors.toMap(FileStorage::getStorageType, s -> s));
    }

    public FileStorage resolve(Enum<?> storageType) {
        FileStorage storage = storageMap.get(storageType);
        if (storage == null) {
            throw new IllegalArgumentException(
                    "No FileStorage registered for storage type: " + storageType);
        }
        return storage;
    }

}
