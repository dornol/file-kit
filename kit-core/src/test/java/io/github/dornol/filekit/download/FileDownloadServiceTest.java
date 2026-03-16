package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileDownloadServiceTest {

    enum StorageType { LOCAL }

    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);
    FileStorage fileStorage = mock(FileStorage.class);

    FileDownloadService service;

    private final FileMetadata metadata = new FileMetadata(
            "file-key", "test.txt", 5, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL)
    );

    @BeforeEach
    void setUp() {
        service = new FileDownloadService(metadataRepository, storageResolver);
    }

    @Test
    void download_returnsResultWithStream() {
        InputStream content = new ByteArrayInputStream("hello".getBytes());
        when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
        when(fileStorage.load(metadata)).thenReturn(content);

        DownloadResult result = service.download("file-key");

        assertNotNull(result);
        assertEquals(metadata, result.metadata());
        assertEquals(content, result.content());
    }

    @Test
    void download_throwsWhenFileNotFound() {
        when(metadataRepository.getByKey("missing")).thenThrow(
                new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found: missing"));

        assertThrows(FileStorageException.class, () -> service.download("missing"));
    }

    @Test
    void resolveUri_returnsUri() {
        when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
        when(fileStorage.resolveUri(metadata)).thenReturn("https://example.com/file");

        String uri = service.resolveUri("file-key");
        assertEquals("https://example.com/file", uri);
    }

    // ── Constructor validation ───────────────────────────────────────

    @Test
    void nullMetadataRepository_throws() {
        assertThrows(NullPointerException.class,
                () -> new FileDownloadService(null, storageResolver));
    }

    @Test
    void nullStorageResolver_throws() {
        assertThrows(NullPointerException.class,
                () -> new FileDownloadService(metadataRepository, null));
    }

    @Test
    void resolveUri_throwsWhenFileNotFound() {
        when(metadataRepository.getByKey("missing")).thenThrow(
                new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found: missing"));

        assertThrows(FileStorageException.class, () -> service.resolveUri("missing"));
    }

}
