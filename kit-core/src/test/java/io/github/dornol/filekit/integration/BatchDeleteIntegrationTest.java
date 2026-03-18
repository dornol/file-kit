package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.delete.BatchDeleteResult;
import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for batch delete operations.
 */
class BatchDeleteIntegrationTest {

    enum StorageType { MEMORY }

    private InMemoryFileStorage memoryStorage;
    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;
    private FileDeleteService deleteService;

    @BeforeEach
    void setUp() {
        memoryStorage = new InMemoryFileStorage(StorageType.MEMORY);
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(memoryStorage));

        uploadService = new FileUploadService(new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"), storageResolver);
        downloadService = new FileDownloadService(metadataRepository, storageResolver);
        deleteService = new FileDeleteService(metadataRepository, storageResolver);
    }

    @Test
    void deleteAll_allRemoved() throws IOException {
        FileMetadata m1 = uploadService.upload(
                new TestFileSource("a.txt", "aaa".getBytes()), StorageType.MEMORY, "bucket");
        FileMetadata m2 = uploadService.upload(
                new TestFileSource("b.txt", "bbb".getBytes()), StorageType.MEMORY, "bucket");
        FileMetadata m3 = uploadService.upload(
                new TestFileSource("c.txt", "ccc".getBytes()), StorageType.MEMORY, "bucket");

        assertEquals(3, memoryStorage.size());

        BatchDeleteResult result = deleteService.deleteAll(List.of(m1.key(), m2.key(), m3.key()));

        assertTrue(result.allSucceeded());
        assertEquals(3, result.succeeded().size());
        assertEquals(0, memoryStorage.size());
        assertEquals(0, metadataRepository.count());
    }

    @Test
    void deleteAll_partialFailure() throws IOException {
        FileMetadata m1 = uploadService.upload(
                new TestFileSource("a.txt", "aaa".getBytes()), StorageType.MEMORY, "bucket");
        FileMetadata m2 = uploadService.upload(
                new TestFileSource("b.txt", "bbb".getBytes()), StorageType.MEMORY, "bucket");

        BatchDeleteResult result = deleteService.deleteAll(
                List.of(m1.key(), m2.key(), "non-existent-key"));

        assertFalse(result.allSucceeded());
        assertEquals(2, result.succeeded().size());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().containsKey("non-existent-key"));
        assertEquals(3, result.totalRequested());
    }

    @Test
    void deleteAll_afterDelete_downloadThrows() throws IOException {
        FileMetadata m1 = uploadService.upload(
                new TestFileSource("a.txt", "aaa".getBytes()), StorageType.MEMORY, "bucket");
        FileMetadata m2 = uploadService.upload(
                new TestFileSource("b.txt", "bbb".getBytes()), StorageType.MEMORY, "bucket");

        deleteService.deleteAll(List.of(m1.key(), m2.key()));

        FileStorageException ex1 = assertThrows(FileStorageException.class,
                () -> downloadService.download(m1.key()));
        assertEquals(FileStorageException.FILE_NOT_FOUND, ex1.getMessageKey());

        FileStorageException ex2 = assertThrows(FileStorageException.class,
                () -> downloadService.download(m2.key()));
        assertEquals(FileStorageException.FILE_NOT_FOUND, ex2.getMessageKey());
    }
}
