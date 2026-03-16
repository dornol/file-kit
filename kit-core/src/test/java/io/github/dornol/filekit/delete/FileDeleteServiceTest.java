package io.github.dornol.filekit.delete;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
