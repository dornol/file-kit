package io.github.dornol.filekit.archive;

import java.util.List;

/**
 * Metadata for an archive file, including its entries.
 *
 * @param entryCount            number of entries in the archive
 * @param totalUncompressedSize total uncompressed size of all entries in bytes
 * @param entries               list of archive entries (defensive copy)
 */
public record ArchiveMetadata(
        int entryCount,
        long totalUncompressedSize,
        List<ArchiveEntry> entries
) {
    public ArchiveMetadata {
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative: " + entryCount);
        }
        if (totalUncompressedSize < 0) {
            throw new IllegalArgumentException("totalUncompressedSize must not be negative: " + totalUncompressedSize);
        }
        entries = List.copyOf(entries);
    }
}
