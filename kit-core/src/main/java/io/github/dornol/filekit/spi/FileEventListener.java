package io.github.dornol.filekit.spi;

import io.github.dornol.filekit.domain.FileMetadata;

/**
 * SPI for receiving file lifecycle events.
 *
 * <p>All methods have default no-op implementations so listeners
 * can override only the events they care about.</p>
 */
public interface FileEventListener {

    default void onUploaded(FileMetadata metadata) {}

    default void onDownloaded(FileMetadata metadata) {}

    default void onDeleted(FileMetadata metadata) {}

    default void onCopied(FileMetadata source, FileMetadata copy) {}

    default void onMoved(FileMetadata source, FileMetadata moved) {}

    default void onRenamed(FileMetadata before, FileMetadata after) {}

    /**
     * Called when an upload fails <b>after</b> the file content was successfully
     * written to storage. By the time this fires, file-kit has already attempted
     * to delete the file from storage.
     *
     * <p>Common triggers:
     * <ul>
     *   <li>The {@link io.github.dornol.filekit.upload.UploadCallback} threw —
     *       {@code cause} will be a
     *       {@link io.github.dornol.filekit.storage.FileStorageException}
     *       with {@link io.github.dornol.filekit.storage.FileStorageException#CALLBACK_FAILED}.
     *       Its {@link Throwable#getCause()} is the original callback exception.</li>
     *   <li>{@link FileMetadataRepository#save} threw — {@code cause} is the
     *       repository exception as-is (e.g. {@code SQLException},
     *       {@code DataAccessException}). Not wrapped by file-kit.</li>
     * </ul>
     *
     * <p>If storage cleanup itself failed, {@code cause.getSuppressed()}
     * contains the cleanup exception.
     *
     * <p><b>metadata is NOT persisted.</b> It is the in-memory instance that
     * would have been saved. Calling {@link FileMetadataRepository#getByKey}
     * with {@code metadata.key()} will throw not-found.
     *
     * <p><b>Do NOT delete storage again.</b> file-kit has already attempted it;
     * a second delete is at best a no-op. Typical uses: decrement external
     * quota counter, record audit log, emit failure metric.
     *
     * <p>Exceptions thrown from this method are swallowed and logged at WARN.
     *
     * @since 0.1.14
     */
    default void onUploadFailed(FileMetadata metadata, Throwable cause) {}
}
