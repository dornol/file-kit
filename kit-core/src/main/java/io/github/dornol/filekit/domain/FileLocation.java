package io.github.dornol.filekit.domain;

import java.util.Objects;

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
    public FileLocation {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(storageType, "storageType");
        if (!bucket.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid bucket name: " + bucket);
        }
    }
}
