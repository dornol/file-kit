package io.github.dornol.filekit.delete;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.AbstractFileOperationService;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final FileEventPublisher eventPublisher;

    /**
     * Creates a builder with the two required dependencies.
     *
     * @param metadataRepository repository for file metadata lookup and deletion
     * @param storageResolver    resolver to find the storage backend for each file
     * @return a new builder instance
     */
    public static Builder builder(FileMetadataRepository metadataRepository,
                                  FileStorageResolver storageResolver) {
        return new Builder(metadataRepository, storageResolver);
    }

    private FileDeleteService(Builder b) {
        super(b.metadataRepository, b.storageResolver);
        this.eventPublisher = Objects.requireNonNull(b.eventPublisher, "eventPublisher");
    }

    public static final class Builder {

        private final FileMetadataRepository metadataRepository;
        private final FileStorageResolver storageResolver;
        private FileEventPublisher eventPublisher = new FileEventPublisher(List.of());

        private Builder(FileMetadataRepository metadataRepository,
                        FileStorageResolver storageResolver) {
            this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
            this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        }

        /** @param eventPublisher publisher for file lifecycle events */
        public Builder eventPublisher(FileEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public FileDeleteService build() {
            return new FileDeleteService(this);
        }
    }

    /**
     * Deletes a file by its key: removes the metadata record first,
     * then deletes the physical file from storage.
     *
     * <p>Metadata is deleted first so that a partial failure (metadata deleted
     * but storage deletion fails) leaves an orphan file in storage rather than
     * a metadata entry pointing to a missing file. Orphan storage files are
     * harmless and inaccessible, whereas orphan metadata would cause download
     * failures.</p>
     *
     * @param fileKey unique file key
     */
    public void delete(String fileKey) {
        Objects.requireNonNull(fileKey, "fileKey");

        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        metadataRepository.deleteByKey(fileKey);
        resolveStorage(metadata).delete(metadata);

        log.info("File deleted: key={}", fileKey);
        eventPublisher.fireDeleted(metadata);
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

        List<String> succeeded = new ArrayList<>();
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
