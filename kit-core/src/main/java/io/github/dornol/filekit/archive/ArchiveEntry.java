package io.github.dornol.filekit.archive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single entry within an archive file.
 *
 * @param path             entry path within the archive
 * @param compressedSize   compressed size in bytes
 * @param uncompressedSize uncompressed size in bytes
 * @param lastModified     last modification time, or {@code null} if unknown
 * @param directory        whether this entry is a directory
 */
public record ArchiveEntry(
        String path,
        long compressedSize,
        long uncompressedSize,
        @Nullable Instant lastModified,
        boolean directory
) {
    public ArchiveEntry {
        Objects.requireNonNull(path, "path");
        if (compressedSize < 0) {
            throw new IllegalArgumentException("compressedSize must not be negative: " + compressedSize);
        }
        if (uncompressedSize < 0) {
            throw new IllegalArgumentException("uncompressedSize must not be negative: " + uncompressedSize);
        }
    }
}
