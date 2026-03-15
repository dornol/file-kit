package io.github.dornol.filekit.domain;

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
}
