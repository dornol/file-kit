package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Orchestrates the file upload flow: checksum deduplication, format detection,
 * storage delegation, and metadata persistence.
 *
 * @see FileStorage
 * @see FileMetadataRepository
 */
public class FileUploadService {

    private final ChecksumCalculator checksumCalculator;
    private final FileMetadataRepository metadataRepository;
    private final FileFormatExtractor formatExtractor;
    private final FileStorageResolver storageResolver;

    public FileUploadService(ChecksumCalculator checksumCalculator,
                             FileMetadataRepository metadataRepository,
                             FileFormatExtractor formatExtractor,
                             FileStorageResolver storageResolver) {
        this.checksumCalculator = checksumCalculator;
        this.metadataRepository = metadataRepository;
        this.formatExtractor = formatExtractor;
        this.storageResolver = storageResolver;
    }

    public FileMetadata upload(FileSource fileSource, Enum<?> storageType, String bucket) throws IOException {
        byte[] bytes = fileSource.getInputStream().readAllBytes();

        String checksum = checksumCalculator.checksum(bytes);
        FileMetadata existing = metadataRepository.findByChecksum(checksum);
        if (existing != null) {
            return existing;
        }

        FileFormat format = formatExtractor.extract(new ByteArrayInputStream(bytes));

        String key = UUID.randomUUID().toString();
        FileUploadCommand command = new FileUploadCommand(
                key,
                fileSource.getOriginalFilename(),
                bytes,
                format.mimeType(),
                format.extension(),
                bucket
        );

        FileLocation location = storageResolver.resolve(storageType).upload(command);

        FileMetadata metadata = new FileMetadata(
                key,
                fileSource.getOriginalFilename(),
                bytes.length,
                checksum,
                format,
                location
        );

        return metadataRepository.save(metadata);
    }

}
