package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.spring.storage.SpringFileStorage;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

/**
 * Spring-aware download service that returns files as Spring {@link Resource} objects.
 *
 * <p>Delegates to {@link SpringFileStorage#loadResource(FileMetadata)} when the
 * storage implements {@link SpringFileStorage}, otherwise wraps the stream
 * in an {@link InputStreamResource}.</p>
 */
public class SpringDownloadService {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageResolver storageResolver;

    public SpringDownloadService(FileMetadataRepository metadataRepository,
                                 FileStorageResolver storageResolver) {
        this.metadataRepository = metadataRepository;
        this.storageResolver = storageResolver;
    }

    public Resource loadResource(String fileKey) {
        FileMetadata metadata = findMetadataOrThrow(fileKey);
        FileStorage storage = storageResolver.resolve(metadata.location().storageType());

        if (storage instanceof SpringFileStorage springStorage) {
            return springStorage.loadResource(metadata);
        }

        return new InputStreamResource(storage.load(metadata));
    }

    private FileMetadata findMetadataOrThrow(String fileKey) {
        FileMetadata metadata = metadataRepository.findByKey(fileKey);
        if (metadata == null) {
            throw new FileStorageException(FileStorageException.FILE_NOT_FOUND,
                    "File not found: " + fileKey);
        }
        return metadata;
    }

}
