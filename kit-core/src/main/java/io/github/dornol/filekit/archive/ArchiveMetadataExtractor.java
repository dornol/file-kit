package io.github.dornol.filekit.archive;

import java.io.ByteArrayInputStream;
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
        } catch (java.io.IOException e) {
            throw new io.github.dornol.filekit.storage.FileStorageException(
                    io.github.dornol.filekit.storage.FileStorageException.ARCHIVE_PROCESSING_FAILED,
                    "Failed to read archive stream", e);
        }
    }
}
