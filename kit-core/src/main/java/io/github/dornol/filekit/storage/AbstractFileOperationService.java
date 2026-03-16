package io.github.dornol.filekit.storage;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;

import java.util.Objects;

/**
 * Base class for services that operate on stored files (download, delete, etc.).
 *
 * <p>Provides shared infrastructure: metadata lookup by key and storage resolution.</p>
 */
public abstract class AbstractFileOperationService {

    protected final FileMetadataRepository metadataRepository;
    protected final FileStorageResolver storageResolver;

    protected AbstractFileOperationService(FileMetadataRepository metadataRepository,
                                           FileStorageResolver storageResolver) {
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
    }

    /**
     * Resolves the {@link FileStorage} for the given metadata's storage type.
     */
    protected FileStorage resolveStorage(FileMetadata metadata) {
        return storageResolver.resolve(metadata.location().storageType());
    }

}
