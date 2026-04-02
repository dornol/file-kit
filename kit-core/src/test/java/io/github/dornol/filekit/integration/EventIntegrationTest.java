package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.spi.FileEventListener;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.transfer.FileTransferService;
import io.github.dornol.filekit.upload.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EventIntegrationTest {

    enum StorageType { A, B }

    private InMemoryFileStorage storageA;
    private InMemoryFileStorage storageB;
    private InMemoryMetadataRepository metadataRepository;
    private FileEventListener listener;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;
    private FileDeleteService deleteService;
    private FileTransferService transferService;

    @BeforeEach
    void setUp() {
        storageA = new InMemoryFileStorage(StorageType.A);
        storageB = new InMemoryFileStorage(StorageType.B);
        metadataRepository = new InMemoryMetadataRepository();
        listener = mock(FileEventListener.class);
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(storageA, storageB));
        FileEventPublisher eventPublisher = new FileEventPublisher(List.of(listener));

        uploadService = FileUploadService.builder(
                new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"),
                storageResolver).eventPublisher(eventPublisher).build();
        downloadService = FileDownloadService.builder(metadataRepository, storageResolver)
                .eventPublisher(eventPublisher).build();
        deleteService = FileDeleteService.builder(metadataRepository, storageResolver)
                .eventPublisher(eventPublisher).build();
        transferService = FileTransferService.builder(metadataRepository, storageResolver)
                .eventPublisher(eventPublisher).build();
    }

    @Nested
    class UploadEvent {

        @Test
        void upload_firesUploadedEvent() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            verify(listener).onUploaded(meta);
        }

        @Test
        void dedup_doesNotFireEvent() throws IOException {
            byte[] content = "dedup".getBytes();
            uploadService.upload(new TestFileSource("a.txt", content), StorageType.A, "bucket");

            // Reset mock to track second call
            org.mockito.Mockito.reset(listener);

            uploadService.upload(new TestFileSource("b.txt", content), StorageType.A, "bucket");

            verify(listener, never()).onUploaded(any());
        }
    }

    @Nested
    class DownloadEvent {

        @Test
        void download_firesDownloadedEvent() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            downloadService.download(meta.key()).content().close();

            verify(listener).onDownloaded(meta);
        }
    }

    @Nested
    class DeleteEvent {

        @Test
        void delete_firesDeletedEvent() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            deleteService.delete(meta.key());

            verify(listener).onDeleted(meta);
        }
    }

    @Nested
    class CopyEvent {

        @Test
        void copy_firesCopiedEvent() throws IOException {
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket-a");

            FileMetadata copied = transferService.copy(source.key(), StorageType.B, "bucket-b");

            verify(listener).onCopied(source, copied);
            verify(listener, never()).onMoved(any(), any());
        }
    }

    @Nested
    class MoveEvent {

        @Test
        void move_firesMovedEvent_notCopied() throws IOException {
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket-a");

            // Reset to ignore upload event
            org.mockito.Mockito.reset(listener);

            FileMetadata moved = transferService.move(source.key(), StorageType.B, "bucket-b");

            verify(listener).onMoved(source, moved);
            verify(listener, never()).onCopied(any(), any());
        }
    }

    @Nested
    class BatchDeleteEvent {

        @Test
        void batchDelete_firesDeletedEventPerFile() throws IOException {
            FileMetadata meta1 = uploadService.upload(
                    new TestFileSource("a.txt", "content A".getBytes()), StorageType.A, "bucket");
            FileMetadata meta2 = uploadService.upload(
                    new TestFileSource("b.txt", "content B".getBytes()), StorageType.A, "bucket");

            org.mockito.Mockito.reset(listener);

            deleteService.deleteAll(List.of(meta1.key(), meta2.key()));

            verify(listener).onDeleted(meta1);
            verify(listener).onDeleted(meta2);
            assertEquals(0, storageA.size());
        }

        @Test
        void batchDelete_doesNotFireEventForNotFound() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("a.txt", "data".getBytes()), StorageType.A, "bucket");

            org.mockito.Mockito.reset(listener);

            deleteService.deleteAll(List.of(meta.key(), "non-existent-key"));

            verify(listener).onDeleted(meta);
            verify(listener, times(1)).onDeleted(any());
        }
    }

    @Nested
    class MultipleListeners {

        @Test
        void allListenersReceiveEvents() throws IOException {
            FileEventListener listener2 = mock(FileEventListener.class);
            FileEventPublisher multiPublisher = new FileEventPublisher(List.of(listener, listener2));

            FileUploadService uploadSvc = FileUploadService.builder(
                    new Sha256ChecksumCalculator(), metadataRepository,
                    is -> new FileFormat("text/plain", "txt", "text"),
                    new FileStorageResolver(List.of(storageA, storageB)))
                    .eventPublisher(multiPublisher).build();

            // Need fresh metadata repo to avoid dedup
            FileMetadata meta = uploadSvc.upload(
                    new TestFileSource("multi.txt", "multi listener data".getBytes()),
                    StorageType.A, "bucket");

            verify(listener).onUploaded(meta);
            verify(listener2).onUploaded(meta);
        }
    }

    @Nested
    class FullLifecycle {

        @Test
        void uploadDownloadDelete_allEventsInOrder() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("lifecycle.txt", "lifecycle data".getBytes()),
                    StorageType.A, "bucket");

            verify(listener).onUploaded(meta);

            downloadService.download(meta.key()).content().close();

            verify(listener).onDownloaded(meta);

            deleteService.delete(meta.key());

            verify(listener).onDeleted(meta);
        }
    }

    @Nested
    class ListenerException {

        @Test
        void listenerException_doesNotBreakUpload() throws IOException {
            doThrow(new RuntimeException("boom")).when(listener).onUploaded(any());

            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            assertNotNull(meta);
            assertEquals(1, storageA.size());
        }

        @Test
        void listenerException_doesNotBreakDelete() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            doThrow(new RuntimeException("boom")).when(listener).onDeleted(any());

            deleteService.delete(meta.key());

            assertEquals(0, storageA.size());
        }

        @Test
        void listenerException_doesNotBreakCopy() throws IOException {
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket-a");

            doThrow(new RuntimeException("boom")).when(listener).onCopied(any(), any());

            FileMetadata copied = transferService.copy(source.key(), StorageType.B, "bucket-b");

            assertNotNull(copied);
            assertEquals(1, storageB.size());
        }

        @Test
        void listenerException_doesNotBreakMove() throws IOException {
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket-a");

            doThrow(new RuntimeException("boom")).when(listener).onMoved(any(), any());

            FileMetadata moved = transferService.move(source.key(), StorageType.B, "bucket-b");

            assertNotNull(moved);
            assertEquals(1, storageB.size());
            assertEquals(0, storageA.size());
        }
    }
}
