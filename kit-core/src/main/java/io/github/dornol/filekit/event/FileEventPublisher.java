package io.github.dornol.filekit.event;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Publishes file lifecycle events to registered {@link FileEventListener}s.
 *
 * <p>Listener exceptions are logged and swallowed (fire-and-forget) so that
 * a failing listener never breaks the file operation itself.</p>
 */
public class FileEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FileEventPublisher.class);

    private final List<FileEventListener> listeners;

    /**
     * @param listeners list of listeners to notify; empty list = no-op
     */
    public FileEventPublisher(List<FileEventListener> listeners) {
        this.listeners = Objects.requireNonNull(listeners, "listeners");
    }

    public void fireUploaded(FileMetadata metadata) {
        for (FileEventListener listener : listeners) {
            try {
                listener.onUploaded(metadata);
            } catch (Exception e) {
                log.warn("FileEventListener.onUploaded failed: {}", e.getMessage(), e);
            }
        }
    }

    public void fireDownloaded(FileMetadata metadata) {
        for (FileEventListener listener : listeners) {
            try {
                listener.onDownloaded(metadata);
            } catch (Exception e) {
                log.warn("FileEventListener.onDownloaded failed: {}", e.getMessage(), e);
            }
        }
    }

    public void fireDeleted(FileMetadata metadata) {
        for (FileEventListener listener : listeners) {
            try {
                listener.onDeleted(metadata);
            } catch (Exception e) {
                log.warn("FileEventListener.onDeleted failed: {}", e.getMessage(), e);
            }
        }
    }

    public void fireCopied(FileMetadata source, FileMetadata copy) {
        for (FileEventListener listener : listeners) {
            try {
                listener.onCopied(source, copy);
            } catch (Exception e) {
                log.warn("FileEventListener.onCopied failed: {}", e.getMessage(), e);
            }
        }
    }

    public void fireMoved(FileMetadata source, FileMetadata moved) {
        for (FileEventListener listener : listeners) {
            try {
                listener.onMoved(source, moved);
            } catch (Exception e) {
                log.warn("FileEventListener.onMoved failed: {}", e.getMessage(), e);
            }
        }
    }
}
