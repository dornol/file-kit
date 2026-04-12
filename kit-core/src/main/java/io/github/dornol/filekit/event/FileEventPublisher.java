package io.github.dornol.filekit.event;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
        this.listeners = List.copyOf(listeners);
    }

    public void fireUploaded(FileMetadata metadata) {
        dispatch("onUploaded", listener -> listener.onUploaded(metadata));
    }

    public void fireDownloaded(FileMetadata metadata) {
        dispatch("onDownloaded", listener -> listener.onDownloaded(metadata));
    }

    public void fireDeleted(FileMetadata metadata) {
        dispatch("onDeleted", listener -> listener.onDeleted(metadata));
    }

    public void fireCopied(FileMetadata source, FileMetadata copy) {
        dispatch("onCopied", listener -> listener.onCopied(source, copy));
    }

    public void fireMoved(FileMetadata source, FileMetadata moved) {
        dispatch("onMoved", listener -> listener.onMoved(source, moved));
    }

    private void dispatch(String eventName, Consumer<FileEventListener> action) {
        for (FileEventListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.warn("FileEventListener.{} failed: {}", eventName, e.getMessage(), e);
            }
        }
    }
}
