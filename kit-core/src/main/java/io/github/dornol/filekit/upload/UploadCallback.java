package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileMetadata;

/**
 * Callback executed after a file is uploaded but before metadata is persisted.
 *
 * <p>If the callback throws, the uploaded file is deleted from storage
 * and the exception is propagated. This allows transactional-style
 * integration with business logic:</p>
 *
 * <pre>{@code
 * uploadService.upload(file, StorageType.LOCAL, "uploads", metadata -> {
 *     businessService.process(metadata);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface UploadCallback {

    /**
     * Called with the file metadata after the file has been stored.
     *
     * @param metadata metadata of the uploaded file
     * @throws Exception if business logic fails (triggers file deletion)
     */
    void onUploaded(FileMetadata metadata) throws Exception;

}
