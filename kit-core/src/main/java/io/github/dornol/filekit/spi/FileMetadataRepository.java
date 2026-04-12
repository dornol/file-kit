package io.github.dornol.filekit.spi;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorageException;
import org.jspecify.annotations.Nullable;

/**
 * Persistence interface for file metadata.
 *
 * <p>Implement this to store and retrieve {@link FileMetadata}
 * using your preferred data store (RDBMS, NoSQL, in-memory, etc.).</p>
 */
public interface FileMetadataRepository {

    /**
     * Finds metadata by content checksum, used for deduplication.
     *
     * @param checksum content checksum
     * @return matching metadata, or {@code null} if not found
     */
    @Nullable
    FileMetadata findByChecksum(String checksum);

    /**
     * Finds metadata by unique file key.
     *
     * @param key unique file key
     * @return matching metadata, or {@code null} if not found
     */
    @Nullable
    FileMetadata findByKey(String key);

    /**
     * Persists file metadata.
     *
     * @param metadata metadata to save
     * @return the saved metadata
     */
    FileMetadata save(FileMetadata metadata);

    /**
     * Deletes metadata by unique file key.
     *
     * @param key unique file key
     */
    void deleteByKey(String key);

    /**
     * Checks whether metadata exists for the given file key.
     *
     * <p>The default implementation delegates to {@link #findByKey(String)},
     * which loads the full metadata object. For large-scale deployments,
     * override this with a more efficient query
     * (e.g. {@code SELECT 1 WHERE key = ?}).</p>
     *
     * @param key unique file key
     * @return {@code true} if metadata exists
     */
    default boolean existsByKey(String key) {
        return findByKey(key) != null;
    }

    /**
     * Replaces the metadata for the given key with the provided instance.
     *
     * <p>The default implementation verifies the key exists (via {@link #getByKey(String)}),
     * then delegates to {@link #save(FileMetadata)}.
     * Override if your data store can do this atomically (e.g. SQL UPDATE).</p>
     *
     * @param metadata the updated metadata (same key, possibly different fields)
     * @return the persisted metadata
     * @throws FileStorageException if no metadata exists for the given key
     */
    default FileMetadata update(FileMetadata metadata) {
        getByKey(metadata.key());
        return save(metadata);
    }

    /**
     * Finds metadata by key, throwing {@link FileStorageException} if not found.
     *
     * @param key unique file key
     * @return matching metadata (never null)
     * @throws FileStorageException if no metadata exists for the given key
     */
    default FileMetadata getByKey(String key) {
        FileMetadata metadata = findByKey(key);
        if (metadata == null) {
            throw new FileStorageException(FileStorageException.FILE_NOT_FOUND,
                    "File not found: " + key);
        }
        return metadata;
    }

}
