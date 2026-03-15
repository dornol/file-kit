package io.github.dornol.filekit.domain;

import java.util.Objects;

/**
 * Metadata describing a stored file.
 *
 * @param key      unique identifier for the file
 * @param name     original filename as provided by the client
 * @param size     file size in bytes
 * @param checksum content checksum (algorithm determined by {@link io.github.dornol.filekit.spi.ChecksumCalculator})
 * @param format   detected file format
 * @param location physical storage location
 */
public record FileMetadata(
        String key,
        String name,
        long size,
        String checksum,
        FileFormat format,
        FileLocation location
) {
    public FileMetadata {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative: " + size);
        }
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(location, "location");
    }
}
