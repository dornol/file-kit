package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.upload.BatchUploadResult;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchUploadIntegrationTest {

    enum StorageType { MEMORY }

    private InMemoryFileStorage memoryStorage;
    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;

    @BeforeEach
    void setUp() {
        memoryStorage = new InMemoryFileStorage(StorageType.MEMORY);
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver resolver = new FileStorageResolver(List.of(memoryStorage));

        uploadService = FileUploadService.builder(new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"), resolver).build();
    }

    @Test
    void uploadAll_allSucceed() {
        BatchUploadResult result = uploadService.uploadAll(
                List.of(
                        new TestFileSource("a.txt", "aaa".getBytes()),
                        new TestFileSource("b.txt", "bbb".getBytes()),
                        new TestFileSource("c.txt", "ccc".getBytes())
                ),
                StorageType.MEMORY, "bucket");

        assertTrue(result.allSucceeded());
        assertEquals(3, result.succeeded().size());
        assertEquals(0, result.failed().size());
        assertEquals(3, result.totalRequested());
        assertEquals(3, memoryStorage.size());
    }

    @Test
    void uploadAll_partialFailure() {
        FileUploadService limited = FileUploadService.builder(
                new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"),
                new FileStorageResolver(List.of(memoryStorage)))
                .maxUploadSize(5).build();

        BatchUploadResult result = limited.uploadAll(
                List.of(
                        new TestFileSource("small.txt", "hi".getBytes()),
                        new TestFileSource("big.txt", "this is way too long".getBytes())
                ),
                StorageType.MEMORY, "bucket");

        assertFalse(result.allSucceeded());
        assertEquals(1, result.succeeded().size());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().containsKey("big.txt"));
    }

    @Test
    void uploadAll_emptyCollection() {
        BatchUploadResult result = uploadService.uploadAll(
                List.of(), StorageType.MEMORY, "bucket");

        assertTrue(result.allSucceeded());
        assertEquals(0, result.totalRequested());
    }

    @Test
    void uploadAll_dedup() {
        byte[] content = "same content".getBytes();

        BatchUploadResult result = uploadService.uploadAll(
                List.of(
                        new TestFileSource("first.txt", content),
                        new TestFileSource("second.txt", content)
                ),
                StorageType.MEMORY, "bucket");

        assertTrue(result.allSucceeded());
        assertEquals(2, result.succeeded().size());
        // Dedup means same key returned for both
        assertEquals(result.succeeded().get(0).key(), result.succeeded().get(1).key());
        assertEquals(1, memoryStorage.size());
    }
}
