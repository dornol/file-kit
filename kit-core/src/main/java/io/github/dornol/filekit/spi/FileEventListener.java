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
}
