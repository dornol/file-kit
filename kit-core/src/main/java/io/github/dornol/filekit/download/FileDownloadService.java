package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorageResolver;

import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Provides file download and URI resolution by file key.
 *
 * @see FileMetadataRepository
 */
public class FileDownloadService {

    private static final Logger log = Logger.getLogger(FileDownloadService.class.getName());

    private final FileMetadataRepository metadataRepository;
    private final FileStorageResolver storageResolver;

    public FileDownloadService(FileMetadataRepository metadataRepository,
                               FileStorageResolver storageResolver) {
        this.metadataRepository = metadataRepository;
        this.storageResolver = storageResolver;
    }

    public DownloadResult download(String fileKey) {
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        InputStream content = storageResolver
                .resolve(metadata.location().storageType())
                .load(metadata);
        log.info("File downloaded: key=" + fileKey);
        return new DownloadResult(metadata, content);
    }

    public String resolveUri(String fileKey) {
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        return storageResolver
                .resolve(metadata.location().storageType())
                .resolveUri(metadata);
    }

}
