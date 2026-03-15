package io.github.dornol.filekit.spi;

import io.github.dornol.filekit.domain.FileMetadata;
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

}
