package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;

import java.io.InputStream;

/**
 * Provides file download and URI resolution by file key.
 *
 * @see FileStorage
 * @see FileMetadataRepository
 */
public class FileDownloadService {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageResolver storageResolver;

    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver) {
        this.metadataRepository = metadataRepository;
        this.storageResolver = storageResolver;
    }

    public DownloadResult download(String fileKey) {
        FileMetadata metadata = findMetadataOrThrow(fileKey);
        InputStream content = storageResolver
                .resolve(metadata.location().storageType())
                .load(metadata);
        return new DownloadResult(metadata, content);
    }

    public String resolveUri(String fileKey) {
        FileMetadata metadata = findMetadataOrThrow(fileKey);
        return storageResolver
                .resolve(metadata.location().storageType())
                .resolveUri(metadata);
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
