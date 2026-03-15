package io.github.dornol.filekit.domain;

/**
 * Physical storage location of an uploaded file.
 *
 * @param bucket      storage bucket or container name
 * @param objectKey   unique object key within the bucket
 * @param storageType the storage backend that holds this file
 */
public record FileLocation(
        String bucket,
        String objectKey,
        Enum<?> storageType
) {
}
