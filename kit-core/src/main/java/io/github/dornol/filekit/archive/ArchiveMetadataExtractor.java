package io.github.dornol.filekit.archive;

import io.github.dornol.filekit.storage.FileStorageException;

import java.io.IOException;
import java.io.InputStream;

/**
 * SPI for extracting metadata from archive files.
 */
public interface ArchiveMetadataExtractor {

    /**
     * Extracts metadata from archive bytes.
     *
     * @param archiveBytes the archive content
     * @return extracted archive metadata
     */
    ArchiveMetadata extract(byte[] archiveBytes);

    /**
     * Extracts metadata from an archive input stream.
     *
     * @param archiveStream the archive content stream
     * @return extracted archive metadata
     */
    default ArchiveMetadata extract(InputStream archiveStream) {
        try {
            return extract(archiveStream.readAllBytes());
        } catch (IOException e) {
            throw new FileStorageException(
                    FileStorageException.ARCHIVE_PROCESSING_FAILED,
                    "Failed to read archive stream", e);
        }
    }
}
