package io.github.dornol.filekit.metadata;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.validator.FilenameValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Renames a file by updating its metadata without touching storage.
 */
public class FileRenameService {

    private static final Logger log = LoggerFactory.getLogger(FileRenameService.class);

    private final FileMetadataRepository metadataRepository;
    private final FileEventPublisher eventPublisher;

    /**
     * Creates a builder with the required dependency.
     *
     * @param metadataRepository repository for file metadata lookup and update
     * @return a new builder instance
     */
    public static Builder builder(FileMetadataRepository metadataRepository) {
        return new Builder(metadataRepository);
    }

    private FileRenameService(Builder b) {
        this.metadataRepository = Objects.requireNonNull(b.metadataRepository, "metadataRepository");
        this.eventPublisher = Objects.requireNonNull(b.eventPublisher, "eventPublisher");
    }

    public static final class Builder {

        private final FileMetadataRepository metadataRepository;
        private FileEventPublisher eventPublisher = new FileEventPublisher(List.of());

        private Builder(FileMetadataRepository metadataRepository) {
            this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        }

        /** @param eventPublisher publisher for file lifecycle events */
        public Builder eventPublisher(FileEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public FileRenameService build() {
            return new FileRenameService(this);
        }
    }

    /**
     * Renames a file by updating its metadata. Storage is not affected.
     *
     * @param fileKey the key of the file to rename
     * @param newName the new filename
     * @return the updated metadata
     * @throws FileStorageException if the file is not found or the filename is invalid
     */
    public FileMetadata rename(String fileKey, String newName) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(newName, "newName");
        validateFilename(newName);

        FileMetadata before = metadataRepository.getByKey(fileKey);
        FileMetadata after = before.withName(newName);
        FileMetadata saved = metadataRepository.update(after);

        log.info("File renamed: key={}, oldName={}, newName={}", fileKey, before.name(), newName);
        eventPublisher.fireRenamed(before, saved);
        return saved;
    }

    private static void validateFilename(String filename) {
        if (!FilenameValidator.isSafe(filename)) {
            throw new FileStorageException(FileStorageException.INVALID_FILENAME,
                    "Filename is blank, too long, or contains illegal characters");
        }
    }
}
