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

    /** Default maximum total uncompressed size: 1 GB. */
    private static final long DEFAULT_MAX_UNCOMPRESSED_SIZE = 1L * 1024 * 1024 * 1024;

    /** Default maximum number of entries. */
    private static final int DEFAULT_MAX_ENTRIES = 65_535;

    private final long maxUncompressedSize;
    private final int maxEntries;

    public ZipArchiveMetadataExtractor() {
        this(DEFAULT_MAX_UNCOMPRESSED_SIZE, DEFAULT_MAX_ENTRIES);
    }

    /**
     * @param maxUncompressedSize maximum allowed total uncompressed size in bytes
     * @param maxEntries          maximum allowed number of entries
     */
    public ZipArchiveMetadataExtractor(long maxUncompressedSize, int maxEntries) {
        if (maxUncompressedSize <= 0) {
            throw new IllegalArgumentException("maxUncompressedSize must be positive");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxUncompressedSize = maxUncompressedSize;
        this.maxEntries = maxEntries;
    }

    @Override
    public ArchiveMetadata extract(byte[] archiveBytes) {
        return extract(new ByteArrayInputStream(archiveBytes));
    }

    /**
     * Extracts metadata directly from a stream without first buffering the archive.
     * The caller remains responsible for closing the supplied stream.
     */
    @Override
    public ArchiveMetadata extract(java.io.InputStream archiveStream) {
        try {
            ZipInputStream zis = new ZipInputStream(archiveStream);
            List<ArchiveEntry> entries = new ArrayList<>();
            long totalUncompressedSize = 0;

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (entries.size() >= maxEntries) {
                    throw new FileStorageException(FileStorageException.ARCHIVE_PROCESSING_FAILED,
                            "ZIP archive exceeds maximum entry count: " + maxEntries);
                }

                // Always consume the entry and count actual bytes. ZIP header sizes
                // are attacker-controlled and must not be trusted for bomb protection.
                long uncompressedSize = 0;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    if (read > maxUncompressedSize - totalUncompressedSize - uncompressedSize) {
                        throw new FileStorageException(FileStorageException.ARCHIVE_PROCESSING_FAILED,
                                "ZIP archive exceeds maximum uncompressed size: " + maxUncompressedSize);
                    }
                    uncompressedSize += read;
                }

                totalUncompressedSize += uncompressedSize;

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

                zis.closeEntry();
            }

            return new ArchiveMetadata(entries.size(), totalUncompressedSize, entries);
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.ARCHIVE_PROCESSING_FAILED,
                    "Failed to process ZIP archive", e);
        }
    }
}
