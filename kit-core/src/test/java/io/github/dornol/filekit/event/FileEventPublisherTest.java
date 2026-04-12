package io.github.dornol.filekit.event;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEventListener;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileEventPublisherTest {

    enum StorageType { LOCAL }

    FileMetadata metadata = new FileMetadata(
            "key-1", "file.txt", 100, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL)
    );

    FileMetadata metadata2 = new FileMetadata(
            "key-2", "file.txt", 100, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket-2", "obj-key-2", StorageType.LOCAL)
    );

    @Test
    void nullListeners_throws() {
        assertThrows(NullPointerException.class, () -> new FileEventPublisher(null));
    }

    @Test
    void emptyListeners_noError() {
        FileEventPublisher publisher = new FileEventPublisher(List.of());
        assertDoesNotThrow(() -> publisher.fireUploaded(metadata));
        assertDoesNotThrow(() -> publisher.fireDownloaded(metadata));
        assertDoesNotThrow(() -> publisher.fireDeleted(metadata));
        assertDoesNotThrow(() -> publisher.fireCopied(metadata, metadata2));
        assertDoesNotThrow(() -> publisher.fireMoved(metadata, metadata2));
        assertDoesNotThrow(() -> publisher.fireRenamed(metadata, metadata2));
    }

    @Nested
    class ListenerInvocation {

        FileEventListener listener = mock(FileEventListener.class);
        FileEventPublisher publisher = new FileEventPublisher(List.of(listener));

        @Test
        void fireUploaded_callsListener() {
            publisher.fireUploaded(metadata);
            verify(listener).onUploaded(metadata);
        }

        @Test
        void fireDownloaded_callsListener() {
            publisher.fireDownloaded(metadata);
            verify(listener).onDownloaded(metadata);
        }

        @Test
        void fireDeleted_callsListener() {
            publisher.fireDeleted(metadata);
            verify(listener).onDeleted(metadata);
        }

        @Test
        void fireCopied_callsListener() {
            publisher.fireCopied(metadata, metadata2);
            verify(listener).onCopied(metadata, metadata2);
        }

        @Test
        void fireMoved_callsListener() {
            publisher.fireMoved(metadata, metadata2);
            verify(listener).onMoved(metadata, metadata2);
        }

        @Test
        void fireRenamed_callsListener() {
            publisher.fireRenamed(metadata, metadata2);
            verify(listener).onRenamed(metadata, metadata2);
        }
    }

    @Nested
    class MultipleListeners {

        @Test
        void allListenersCalled() {
            FileEventListener listener1 = mock(FileEventListener.class);
            FileEventListener listener2 = mock(FileEventListener.class);
            FileEventPublisher publisher = new FileEventPublisher(List.of(listener1, listener2));

            publisher.fireUploaded(metadata);

            verify(listener1).onUploaded(metadata);
            verify(listener2).onUploaded(metadata);
        }
    }

    @Nested
    class ExceptionHandling {

        @Test
        void listenerException_swallowed() {
            FileEventListener failing = mock(FileEventListener.class);
            doThrow(new RuntimeException("boom")).when(failing).onUploaded(metadata);
            FileEventPublisher publisher = new FileEventPublisher(List.of(failing));

            assertDoesNotThrow(() -> publisher.fireUploaded(metadata));
        }

        @Test
        void failingListener_doesNotBlockOthers() {
            FileEventListener failing = mock(FileEventListener.class);
            FileEventListener healthy = mock(FileEventListener.class);
            doThrow(new RuntimeException("boom")).when(failing).onUploaded(metadata);
            FileEventPublisher publisher = new FileEventPublisher(List.of(failing, healthy));

            publisher.fireUploaded(metadata);

            verify(healthy).onUploaded(metadata);
        }
    }
}
