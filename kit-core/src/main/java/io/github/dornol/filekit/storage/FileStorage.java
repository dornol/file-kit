package io.github.dornol.filekit.storage;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;

import java.io.InputStream;

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
     * Resolves a URI for accessing the file (e.g. a download URL).
     *
     * @param metadata metadata of the file
     * @return URI string
     */
    String resolveUri(FileMetadata metadata);

}
