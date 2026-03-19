package io.github.dornol.filekit.storage;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;

import java.io.InputStream;
import java.time.Duration;

/**
 * Abstraction for a file storage backend (e.g. local filesystem, S3, GCS).
 *
 * <p>Register one or more implementations as beans; they are resolved at
 * runtime by {@link FileStorageResolver} using the {@link #getStorageType() storageType} key.</p>
 */
public interface FileStorage {

    /** Returns the enum constant identifying this storage backend. */
    Enum<?> getStorageType();

    /**
     * Uploads a file and returns its storage location.
     *
     * @param command upload details (key, content, bucket, etc.)
     * @return the resulting storage location
     */
    FileLocation upload(FileUploadCommand command);

    /**
     * Loads the file content as an input stream.
     *
     * @param metadata metadata of the file to load
     * @return input stream of the file content
     */
    InputStream load(FileMetadata metadata);

    /**
     * Deletes a file from storage.
     *
     * @param metadata metadata of the file to delete
     */
    void delete(FileMetadata metadata);

    /**
     * Resolves a URI for accessing the file (e.g. a download URL).
     *
     * @param metadata metadata of the file
     * @return URI string
     */
    String resolveUri(FileMetadata metadata);

    /**
     * Generates a pre-signed URL for direct file access.
     *
     * @param metadata   metadata of the file
     * @param expiration URL expiration duration
     * @return pre-signed URL string
     * @throws UnsupportedOperationException if this storage does not support pre-signed URLs
     */
    default String generatePresignedUrl(FileMetadata metadata, Duration expiration) {
        throw new UnsupportedOperationException(
                "Pre-signed URL generation is not supported by " + getClass().getSimpleName());
    }

    /**
     * Loads a byte range of the file content as an input stream.
     *
     * @param metadata metadata of the file to load
     * @param start    start byte offset (inclusive)
     * @param end      end byte offset (inclusive)
     * @return input stream of the requested byte range
     */
    default InputStream loadRange(FileMetadata metadata, long start, long end) {
        InputStream is = load(metadata);
        try {
            is.skipNBytes(start);
        } catch (java.io.IOException e) {
            try {
                is.close();
            } catch (java.io.IOException closeEx) {
                e.addSuppressed(closeEx);
            }
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "Failed to skip to byte offset " + start, e);
        }
        return new io.github.dornol.filekit.io.BoundedInputStream(is, end - start + 1);
    }

}
