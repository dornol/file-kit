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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        uploadService = new FileUploadService(
                new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"),
                storageResolver, 0, null, new io.github.dornol.filekit.spi.NoOpFileEncryptor(),
                null, eventPublisher);
        downloadService = new FileDownloadService(metadataRepository, storageResolver,
                new io.github.dornol.filekit.spi.NoOpFileEncryptor(), eventPublisher);
        deleteService = new FileDeleteService(metadataRepository, storageResolver, eventPublisher);
        transferService = new FileTransferService(metadataRepository, storageResolver, null, eventPublisher);
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
    class ListenerException {

        @Test
        void listenerException_doesNotBreakUpload() throws IOException {
            doThrow(new RuntimeException("boom")).when(listener).onUploaded(any());

            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            // Upload still succeeds
            org.junit.jupiter.api.Assertions.assertNotNull(meta);
            org.junit.jupiter.api.Assertions.assertEquals(1, storageA.size());
        }

        @Test
        void listenerException_doesNotBreakDelete() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            doThrow(new RuntimeException("boom")).when(listener).onDeleted(any());

            deleteService.delete(meta.key());

            // Delete still succeeds
            org.junit.jupiter.api.Assertions.assertEquals(0, storageA.size());
        }
    }
}
