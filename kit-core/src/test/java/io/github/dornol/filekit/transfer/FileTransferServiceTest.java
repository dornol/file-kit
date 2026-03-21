package io.github.dornol.filekit.transfer;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.quota.QuotaChecker;
import io.github.dornol.filekit.spi.FileEventListener;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileTransferServiceTest {

    enum StorageType { LOCAL, S3 }

    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);
    FileStorage sourceStorage = mock(FileStorage.class);
    FileStorage targetStorage = mock(FileStorage.class);

    FileTransferService service;

    private final FileMetadata sourceMetadata = new FileMetadata(
            "source-key", "test.txt", 5, "checksum123",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("source-bucket", "obj-key", StorageType.LOCAL)
    );

    @BeforeEach
    void setUp() {
        service = new FileTransferService(metadataRepository, storageResolver);
    }

    private void setupCopyMocks() {
        when(metadataRepository.getByKey("source-key")).thenReturn(sourceMetadata);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(sourceStorage);
        when(storageResolver.resolve(StorageType.S3)).thenReturn(targetStorage);
        when(sourceStorage.load(sourceMetadata)).thenReturn(new ByteArrayInputStream("hello".getBytes()));
        when(targetStorage.upload(any(FileUploadCommand.class)))
                .thenReturn(new FileLocation("target-bucket", "new-obj-key", StorageType.S3));
        when(metadataRepository.save(any(FileMetadata.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Copy ────────────────────────────────────────────────────────

    @Nested
    class Copy {

        @Test
        void createsNewKeyAndPreservesMetadata() {
            setupCopyMocks();

            FileMetadata copied = service.copy("source-key", StorageType.S3, "target-bucket");

            assertNotNull(copied);
            assertNotEquals("source-key", copied.key());
            assertEquals("test.txt", copied.name());
            assertEquals(5, copied.size());
            assertEquals("checksum123", copied.checksum());
            assertEquals("text/plain", copied.format().mimeType());
            assertEquals("txt", copied.format().extension());
            assertEquals("text", copied.format().primaryType());
            assertEquals("target-bucket", copied.location().bucket());
        }

        @Test
        void newKeyIsUuidFormat() {
            setupCopyMocks();

            FileMetadata copied = service.copy("source-key", StorageType.S3, "target-bucket");

            // UUID format: 8-4-4-4-12
            assertTrue(copied.key().matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }

        @Test
        void uploadsToTargetStorage() {
            setupCopyMocks();

            service.copy("source-key", StorageType.S3, "target-bucket");

            ArgumentCaptor<FileUploadCommand> captor = ArgumentCaptor.forClass(FileUploadCommand.class);
            verify(targetStorage).upload(captor.capture());

            FileUploadCommand cmd = captor.getValue();
            assertEquals("test.txt", cmd.originalFilename());
            assertEquals("text/plain", cmd.mimeType());
            assertEquals("txt", cmd.extension());
            assertEquals("target-bucket", cmd.bucket());
            assertEquals(5, cmd.contentLength());
        }

        @Test
        void sourceRemainsAfterCopy() {
            setupCopyMocks();

            service.copy("source-key", StorageType.S3, "target-bucket");

            verify(sourceStorage, never()).delete(any());
            verify(metadataRepository, never()).deleteByKey("source-key");
        }

        @Test
        void savesNewMetadata() {
            setupCopyMocks();

            service.copy("source-key", StorageType.S3, "target-bucket");

            ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
            verify(metadataRepository).save(captor.capture());

            FileMetadata saved = captor.getValue();
            assertNotEquals("source-key", saved.key());
            assertEquals("test.txt", saved.name());
            assertEquals("checksum123", saved.checksum());
        }

        @Test
        void copyToSameStorageType() {
            when(metadataRepository.getByKey("source-key")).thenReturn(sourceMetadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(sourceStorage);
            when(sourceStorage.load(sourceMetadata)).thenReturn(new ByteArrayInputStream("hello".getBytes()));
            when(sourceStorage.upload(any(FileUploadCommand.class)))
                    .thenReturn(new FileLocation("other-bucket", "new-obj-key", StorageType.LOCAL));
            when(metadataRepository.save(any(FileMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata copied = service.copy("source-key", StorageType.LOCAL, "other-bucket");

            assertNotNull(copied);
            assertNotEquals("source-key", copied.key());
            assertEquals("other-bucket", copied.location().bucket());
        }

        @Test
        void throwsWhenFileNotFound() {
            when(metadataRepository.getByKey("missing")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.copy("missing", StorageType.S3, "bucket"));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void throwsWhenStorageNotFound() {
            when(metadataRepository.getByKey("source-key")).thenReturn(sourceMetadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(sourceStorage);
            when(storageResolver.resolve(StorageType.S3)).thenThrow(
                    new FileStorageException(FileStorageException.STORAGE_NOT_FOUND, "Storage not found"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.copy("source-key", StorageType.S3, "bucket"));
            assertEquals(FileStorageException.STORAGE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void throwsWhenUploadFails() {
            when(metadataRepository.getByKey("source-key")).thenReturn(sourceMetadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(sourceStorage);
            when(storageResolver.resolve(StorageType.S3)).thenReturn(targetStorage);
            when(sourceStorage.load(sourceMetadata)).thenReturn(new ByteArrayInputStream("hello".getBytes()));
            when(targetStorage.upload(any(FileUploadCommand.class)))
                    .thenThrow(new FileStorageException(FileStorageException.UPLOAD_FAILED, "Upload failed"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.copy("source-key", StorageType.S3, "target-bucket"));
            assertEquals(FileStorageException.UPLOAD_FAILED, ex.getMessageKey());
        }
    }

    // ── Move ────────────────────────────────────────────────────────

    @Nested
    class Move {

        @Test
        void copiesAndDeletesSource() {
            setupCopyMocks();

            FileMetadata moved = service.move("source-key", StorageType.S3, "target-bucket");

            assertNotNull(moved);
            assertNotEquals("source-key", moved.key());
            assertEquals("test.txt", moved.name());
            verify(sourceStorage).delete(sourceMetadata);
            verify(metadataRepository).deleteByKey("source-key");
        }

        @Test
        void deletesMetadataBeforeStorage() {
            setupCopyMocks();

            service.move("source-key", StorageType.S3, "target-bucket");

            var order = inOrder(metadataRepository, sourceStorage);
            order.verify(metadataRepository).deleteByKey("source-key");
            order.verify(sourceStorage).delete(sourceMetadata);
        }

        @Test
        void movedMetadataHasNewLocation() {
            setupCopyMocks();

            FileMetadata moved = service.move("source-key", StorageType.S3, "target-bucket");

            assertEquals("target-bucket", moved.location().bucket());
        }

        @Test
        void throwsWhenFileNotFound() {
            when(metadataRepository.getByKey("missing")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.move("missing", StorageType.S3, "bucket"));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void throwsWhenSourceDeleteFails() {
            setupCopyMocks();
            doThrow(new RuntimeException("Delete failed"))
                    .when(sourceStorage).delete(sourceMetadata);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.move("source-key", StorageType.S3, "target-bucket"));
            assertEquals(FileStorageException.MOVE_FAILED, ex.getMessageKey());
            assertNotNull(ex.getCause());
        }

        @Test
        void throwsWhenMetadataDeleteFails() {
            setupCopyMocks();
            doThrow(new RuntimeException("Metadata delete failed"))
                    .when(metadataRepository).deleteByKey("source-key");

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.move("source-key", StorageType.S3, "target-bucket"));
            assertEquals(FileStorageException.MOVE_FAILED, ex.getMessageKey());
        }

        @Test
        void copyStillExistsAfterMoveFails() {
            setupCopyMocks();
            doThrow(new RuntimeException("Delete failed"))
                    .when(sourceStorage).delete(sourceMetadata);

            assertThrows(FileStorageException.class,
                    () -> service.move("source-key", StorageType.S3, "target-bucket"));

            // Copy was saved before the delete failure
            verify(metadataRepository).save(any(FileMetadata.class));
        }

        @Test
        void moveToSameStorageType() {
            when(metadataRepository.getByKey("source-key")).thenReturn(sourceMetadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(sourceStorage);
            when(sourceStorage.load(sourceMetadata)).thenReturn(new ByteArrayInputStream("hello".getBytes()));
            when(sourceStorage.upload(any(FileUploadCommand.class)))
                    .thenReturn(new FileLocation("other-bucket", "new-obj-key", StorageType.LOCAL));
            when(metadataRepository.save(any(FileMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata moved = service.move("source-key", StorageType.LOCAL, "other-bucket");

            assertNotNull(moved);
            verify(sourceStorage).delete(sourceMetadata);
            verify(metadataRepository).deleteByKey("source-key");
        }
    }

    // ── Parameter validation ────────────────────────────────────────

    @Nested
    class ParameterValidation {

        @Test
        void copyNullFileKey_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.copy(null, StorageType.S3, "bucket"));
        }

        @Test
        void copyNullStorageType_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.copy("key", null, "bucket"));
        }

        @Test
        void copyNullBucket_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.copy("key", StorageType.S3, null));
        }

        @Test
        void moveNullFileKey_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.move(null, StorageType.S3, "bucket"));
        }

        @Test
        void moveNullStorageType_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.move("key", null, "bucket"));
        }

        @Test
        void moveNullBucket_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.move("key", StorageType.S3, null));
        }
    }

    // ── Quota integration ─────────────────────────────────────────────

    @Nested
    class QuotaIntegration {

        QuotaChecker quotaChecker = mock(QuotaChecker.class);
        FileTransferService serviceWithQuota = new FileTransferService(
                metadataRepository, storageResolver, quotaChecker, new FileEventPublisher(List.of()));

        @Test
        void copy_quotaExceeded_throwsBeforeUpload() {
            when(metadataRepository.getByKey("source-key")).thenReturn(sourceMetadata);
            doThrow(new FileStorageException(FileStorageException.QUOTA_EXCEEDED, "Quota exceeded"))
                    .when(quotaChecker).check(StorageType.S3, "target-bucket", 5L);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> serviceWithQuota.copy("source-key", StorageType.S3, "target-bucket"));
            assertEquals(FileStorageException.QUOTA_EXCEEDED, ex.getMessageKey());
            verify(targetStorage, never()).upload(any());
        }

        @Test
        void move_quotaExceeded_throwsBeforeUpload() {
            when(metadataRepository.getByKey("source-key")).thenReturn(sourceMetadata);
            doThrow(new FileStorageException(FileStorageException.QUOTA_EXCEEDED, "Quota exceeded"))
                    .when(quotaChecker).check(StorageType.S3, "target-bucket", 5L);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> serviceWithQuota.move("source-key", StorageType.S3, "target-bucket"));
            assertEquals(FileStorageException.QUOTA_EXCEEDED, ex.getMessageKey());
            verify(sourceStorage, never()).delete(any());
        }

        @Test
        void copy_quotaPasses_uploadsNormally() {
            setupCopyMocks();

            FileMetadata copied = serviceWithQuota.copy("source-key", StorageType.S3, "target-bucket");

            assertNotNull(copied);
            verify(quotaChecker).check(StorageType.S3, "target-bucket", 5L);
            verify(metadataRepository).save(any());
        }

        @Test
        void nullQuotaChecker_skipsCheck() {
            setupCopyMocks();

            FileMetadata copied = service.copy("source-key", StorageType.S3, "target-bucket");

            assertNotNull(copied);
        }
    }

    // ── Event integration ────────────────────────────────────────────

    @Nested
    class EventIntegration {

        FileEventListener listener = mock(FileEventListener.class);
        FileTransferService serviceWithEvents = new FileTransferService(
                metadataRepository, storageResolver, null, new FileEventPublisher(List.of(listener)));

        @Test
        void copy_fires_onCopied() {
            setupCopyMocks();

            FileMetadata copied = serviceWithEvents.copy("source-key", StorageType.S3, "target-bucket");

            verify(listener).onCopied(sourceMetadata, copied);
            verify(listener, never()).onMoved(any(), any());
        }

        @Test
        void move_fires_onMoved_notCopied() {
            setupCopyMocks();

            FileMetadata moved = serviceWithEvents.move("source-key", StorageType.S3, "target-bucket");

            verify(listener).onMoved(sourceMetadata, moved);
            verify(listener, never()).onCopied(any(), any());
        }

        @Test
        void move_deleteFailure_doesNotFireEvent() {
            setupCopyMocks();
            doThrow(new RuntimeException("Delete failed"))
                    .when(sourceStorage).delete(sourceMetadata);

            assertThrows(FileStorageException.class,
                    () -> serviceWithEvents.move("source-key", StorageType.S3, "target-bucket"));

            verify(listener, never()).onMoved(any(), any());
            verify(listener, never()).onCopied(any(), any());
        }

        @Test
        void copy_listenerException_doesNotBreakCopy() {
            setupCopyMocks();
            doThrow(new RuntimeException("boom")).when(listener).onCopied(any(), any());

            FileMetadata copied = serviceWithEvents.copy("source-key", StorageType.S3, "target-bucket");

            assertNotNull(copied);
            verify(metadataRepository).save(any());
        }

        @Test
        void move_listenerException_doesNotBreakMove() {
            setupCopyMocks();
            doThrow(new RuntimeException("boom")).when(listener).onMoved(any(), any());

            FileMetadata moved = serviceWithEvents.move("source-key", StorageType.S3, "target-bucket");

            assertNotNull(moved);
            verify(sourceStorage).delete(sourceMetadata);
            verify(metadataRepository).deleteByKey("source-key");
        }
    }

    // ── Constructor validation ──────────────────────────────────────

    @Nested
    class ConstructorValidation {

        @Test
        void nullMetadataRepository_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileTransferService(null, storageResolver));
        }

        @Test
        void nullStorageResolver_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileTransferService(metadataRepository, null));
        }

        @Test
        void validConstruction() {
            FileTransferService svc = new FileTransferService(metadataRepository, storageResolver);
            assertNotNull(svc);
        }

        @Test
        void fullConstructor_nullQuotaChecker_allowed() {
            FileTransferService svc = new FileTransferService(
                    metadataRepository, storageResolver, null, new FileEventPublisher(List.of()));
            assertNotNull(svc);
        }

        @Test
        void fullConstructor_nullEventPublisher_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileTransferService(metadataRepository, storageResolver, null, null));
        }
    }
}
