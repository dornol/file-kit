package io.github.dornol.filekit.spring.storage;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorage;
import org.springframework.core.io.Resource;

/**
 * Spring-specific extension of {@link FileStorage} that can return
 * a Spring {@link Resource} for more efficient response streaming.
 */
public interface SpringFileStorage extends FileStorage {

    /**
     * Loads the file as a Spring {@link Resource}.
     *
     * @param metadata metadata of the file to load
     * @return Spring Resource representing the file
     */
    Resource loadResource(FileMetadata metadata);

}
