package io.github.dornol.filekit.archive;

import io.github.dornol.filekit.storage.FileStorageException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Default {@link ArchiveMetadataExtractor} implementation using {@link ZipInputStream}.
 */
public class ZipArchiveMetadataExtractor implements ArchiveMetadataExtractor {

    @Override
    public ArchiveMetadata extract(byte[] archiveBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            List<ArchiveEntry> entries = new ArrayList<>();
            long totalUncompressedSize = 0;

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                // Read through the entry to calculate sizes when not available from central directory
                long uncompressedSize = zipEntry.getSize();
                if (uncompressedSize < 0) {
                    // Size unknown from local header; consume entry to determine size
                    long count = 0;
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        count += read;
                    }
                    uncompressedSize = count;
                }

                long compressedSize = Math.max(zipEntry.getCompressedSize(), 0);
                Instant lastModified = zipEntry.getLastModifiedTime() != null
                        ? zipEntry.getLastModifiedTime().toInstant()
                        : null;

                entries.add(new ArchiveEntry(
                        zipEntry.getName(),
                        compressedSize,
                        uncompressedSize,
                        lastModified,
                        zipEntry.isDirectory()
                ));

                totalUncompressedSize += uncompressedSize;
                zis.closeEntry();
            }

            return new ArchiveMetadata(entries.size(), totalUncompressedSize, entries);
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.ARCHIVE_PROCESSING_FAILED,
                    "Failed to process ZIP archive", e);
        }
    }
}
