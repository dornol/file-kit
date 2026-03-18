package io.github.dornol.filekit.delete;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.AbstractFileOperationService;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates file deletion: removes the file from storage and deletes
 * its metadata from the repository.
 *
 * @see FileStorage#delete(FileMetadata)
 * @see FileMetadataRepository#deleteByKey(String)
 */
public class FileDeleteService extends AbstractFileOperationService {

    private static final Logger log = LoggerFactory.getLogger(FileDeleteService.class);

    /**
     * @param metadataRepository repository for file metadata lookup and deletion
     * @param storageResolver    resolver to find the storage backend for each file
     */
    public FileDeleteService(FileMetadataRepository metadataRepository,
                             FileStorageResolver storageResolver) {
        super(metadataRepository, storageResolver);
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
        resolveStorage(metadata).delete(metadata);
        metadataRepository.deleteByKey(fileKey);

        log.info("File deleted: key={}", fileKey);
    }

    /**
     * Deletes multiple files by their keys using a best-effort strategy.
     *
     * <p>Attempts to delete every file and collects results. Does not stop on first failure.</p>
     *
     * @param fileKeys collection of file keys to delete
     * @return result indicating which deletions succeeded and which failed
     */
    public BatchDeleteResult deleteAll(Collection<String> fileKeys) {
        Objects.requireNonNull(fileKeys, "fileKeys");

        java.util.List<String> succeeded = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();

        for (String fileKey : fileKeys) {
            try {
                delete(fileKey);
                succeeded.add(fileKey);
            } catch (Exception e) {
                log.warn("Failed to delete file: key={}", fileKey, e);
                failed.put(fileKey, e.getMessage());
            }
        }

        log.info("Batch delete completed: {} succeeded, {} failed out of {} requested",
                succeeded.size(), failed.size(), fileKeys.size());
        return new BatchDeleteResult(succeeded, failed);
    }

}
