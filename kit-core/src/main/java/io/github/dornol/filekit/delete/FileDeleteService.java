package io.github.dornol.filekit.delete;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Orchestrates file deletion: removes the file from storage and deletes
 * its metadata from the repository.
 *
 * @see FileStorage#delete(FileMetadata)
 * @see FileMetadataRepository#deleteByKey(String)
 */
public class FileDeleteService {

    private static final Logger log = LoggerFactory.getLogger(FileDeleteService.class);

    private final FileMetadataRepository metadataRepository;
    private final FileStorageResolver storageResolver;

    public FileDeleteService(FileMetadataRepository metadataRepository,
                             FileStorageResolver storageResolver) {
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
    }

    /**
     * Deletes a file by its key: removes the physical file from storage,
     * then deletes the metadata record.
     *
     * @param fileKey unique file key
     */
    public void delete(String fileKey) {
        Objects.requireNonNull(fileKey, "fileKey");

        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        FileStorage storage = storageResolver.resolve(metadata.location().storageType());
        storage.delete(metadata);
        metadataRepository.deleteByKey(fileKey);

        log.info("File deleted: key={}", fileKey);
    }

}
