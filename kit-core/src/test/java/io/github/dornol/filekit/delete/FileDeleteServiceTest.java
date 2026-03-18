package io.github.dornol.filekit.delete;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileDeleteServiceTest {

    enum StorageType { LOCAL }

    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);
    FileStorage fileStorage = mock(FileStorage.class);

    FileDeleteService service;

    private final FileMetadata metadata = new FileMetadata(
            "file-key", "test.txt", 5, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL)
    );

    @BeforeEach
    void setUp() {
        service = new FileDeleteService(metadataRepository, storageResolver);
    }

    @Test
    void delete_removesFromStorageAndRepository() {
        when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);

        service.delete("file-key");

        verify(fileStorage).delete(metadata);
        verify(metadataRepository).deleteByKey("file-key");
    }

    @Test
    void delete_throwsWhenFileNotFound() {
        when(metadataRepository.getByKey("missing")).thenThrow(
                new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found: missing"));

        assertThrows(FileStorageException.class, () -> service.delete("missing"));
    }

    @Test
    void delete_nullKey_throws() {
        assertThrows(NullPointerException.class, () -> service.delete(null));
    }

    @Test
    void constructor_nullMetadataRepository_throws() {
        assertThrows(NullPointerException.class,
                () -> new FileDeleteService(null, storageResolver));
    }

    @Test
    void constructor_nullStorageResolver_throws() {
        assertThrows(NullPointerException.class,
                () -> new FileDeleteService(metadataRepository, null));
    }

    // ── Batch Delete ────────────────────────────────────────────────

    @Nested
    class BatchDelete {

        @Test
        void allSucceed() {
            FileMetadata meta1 = new FileMetadata("key1", "a.txt", 1, "c1",
                    new FileFormat("text/plain", "txt", "text"),
                    new FileLocation("bucket", "o1", StorageType.LOCAL));
            FileMetadata meta2 = new FileMetadata("key2", "b.txt", 2, "c2",
                    new FileFormat("text/plain", "txt", "text"),
                    new FileLocation("bucket", "o2", StorageType.LOCAL));

            when(metadataRepository.getByKey("key1")).thenReturn(meta1);
            when(metadataRepository.getByKey("key2")).thenReturn(meta2);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);

            BatchDeleteResult result = service.deleteAll(List.of("key1", "key2"));

            assertTrue(result.allSucceeded());
            assertEquals(2, result.succeeded().size());
            assertEquals(0, result.failed().size());
            assertEquals(2, result.totalRequested());
            verify(fileStorage).delete(meta1);
            verify(fileStorage).delete(meta2);
        }

        @Test
        void partialFailure() {
            FileMetadata meta1 = new FileMetadata("key1", "a.txt", 1, "c1",
                    new FileFormat("text/plain", "txt", "text"),
                    new FileLocation("bucket", "o1", StorageType.LOCAL));

            when(metadataRepository.getByKey("key1")).thenReturn(meta1);
            when(metadataRepository.getByKey("key2")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "not found"));
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);

            BatchDeleteResult result = service.deleteAll(List.of("key1", "key2"));

            assertFalse(result.allSucceeded());
            assertEquals(1, result.succeeded().size());
            assertEquals("key1", result.succeeded().get(0));
            assertEquals(1, result.failed().size());
            assertTrue(result.failed().containsKey("key2"));
        }

        @Test
        void allFail() {
            when(metadataRepository.getByKey("key1")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "not found"));
            when(metadataRepository.getByKey("key2")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "not found"));

            BatchDeleteResult result = service.deleteAll(List.of("key1", "key2"));

            assertFalse(result.allSucceeded());
            assertEquals(0, result.succeeded().size());
            assertEquals(2, result.failed().size());
        }

        @Test
        void emptyCollection_returnsEmptyResult() {
            BatchDeleteResult result = service.deleteAll(List.of());

            assertTrue(result.allSucceeded());
            assertEquals(0, result.totalRequested());
        }

        @Test
        void nullCollection_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.deleteAll(null));
        }

        @Test
        void continuesAfterFailure() {
            FileMetadata meta3 = new FileMetadata("key3", "c.txt", 3, "c3",
                    new FileFormat("text/plain", "txt", "text"),
                    new FileLocation("bucket", "o3", StorageType.LOCAL));

            when(metadataRepository.getByKey("key1")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "not found"));
            when(metadataRepository.getByKey("key2")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "not found"));
            when(metadataRepository.getByKey("key3")).thenReturn(meta3);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);

            BatchDeleteResult result = service.deleteAll(List.of("key1", "key2", "key3"));

            assertEquals(1, result.succeeded().size());
            assertEquals("key3", result.succeeded().get(0));
            assertEquals(2, result.failed().size());
        }

        @Test
        void singleItem() {
            when(metadataRepository.getByKey("key1")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);

            BatchDeleteResult result = service.deleteAll(List.of("key1"));

            assertTrue(result.allSucceeded());
            assertEquals(1, result.totalRequested());
        }
    }
}
