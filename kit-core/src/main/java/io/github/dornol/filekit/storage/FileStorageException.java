package io.github.dornol.filekit.storage;

/**
 * Base exception for file storage operations.
 *
 * <p>Each instance carries a {@link #getMessageKey() messageKey} for i18n support.
 * Applications can map these keys to localized messages, similar to
 * {@code file-kit.validation.*} keys.</p>
 */
public class FileStorageException extends RuntimeException {

    private final String messageKey;

    public FileStorageException(String messageKey, String message) {
        super(message);
        this.messageKey = messageKey;
    }

    public FileStorageException(String messageKey, String message, Throwable cause) {
        super(message, cause);
        this.messageKey = messageKey;
    }

    /**
     * Returns the message key for i18n lookup.
     *
     * @return message key (e.g. {@code "file-kit.storage.file-not-found"})
     */
    public String getMessageKey() {
        return messageKey;
    }

    /** File metadata not found by key or checksum. */
    public static final String FILE_NOT_FOUND = "file-kit.storage.file-not-found";

    /** No {@link FileStorage} registered for the requested storage type. */
    public static final String STORAGE_NOT_FOUND = "file-kit.storage.storage-not-found";

    /** Failed to write file to storage. */
    public static final String UPLOAD_FAILED = "file-kit.storage.upload-failed";

    /** Failed to read file from storage. */
    public static final String DOWNLOAD_FAILED = "file-kit.storage.download-failed";

    /** Failed to delete file from storage. */
    public static final String DELETE_FAILED = "file-kit.storage.delete-failed";

    /** Business callback failed after upload; file has been rolled back. */
    public static final String CALLBACK_FAILED = "file-kit.storage.callback-failed";

    /** File exceeds the configured maximum upload size. */
    public static final String FILE_TOO_LARGE = "file-kit.storage.file-too-large";

    /** Filename is invalid (too long, contains path traversal characters, etc.). */
    public static final String INVALID_FILENAME = "file-kit.storage.invalid-filename";

    /** Virus detected in uploaded file. */
    public static final String VIRUS_DETECTED = "file-kit.storage.virus-detected";

    /** Virus scan failed with an error. */
    public static final String VIRUS_SCAN_ERROR = "file-kit.storage.virus-scan-error";

    /** Image processing (metadata extraction or resize) failed. */
    public static final String IMAGE_PROCESSING_FAILED = "file-kit.image.processing-failed";

}
