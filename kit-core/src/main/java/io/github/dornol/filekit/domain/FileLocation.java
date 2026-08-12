package io.github.dornol.filekit.domain;

import io.github.dornol.filekit.validator.BucketNameValidator;
import io.github.dornol.filekit.validator.StorageKeyValidator;

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
        BucketNameValidator.validate(bucket);
        StorageKeyValidator.validate(objectKey);
    }
}
