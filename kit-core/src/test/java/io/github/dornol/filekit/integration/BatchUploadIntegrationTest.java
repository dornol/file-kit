package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.upload.BatchUploadResult;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchUploadIntegrationTest {

    enum StorageType { MEMORY }

    private InMemoryFileStorage memoryStorage;
    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;

    @BeforeEach
    void setUp() {
        memoryStorage = new InMemoryFileStorage(StorageType.MEMORY);
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver resolver = new FileStorageResolver(List.of(memoryStorage));

        uploadService = FileUploadService.builder(new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"), resolver).build();
        downloadService = FileDownloadService.builder(metadataRepository, resolver).build();
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
        assertEquals(3, metadataRepository.count());
    }

    @Test
    void uploadAll_eachFileDownloadable() throws IOException {
        byte[] contentA = "content A".getBytes();
        byte[] contentB = "content B".getBytes();

        BatchUploadResult result = uploadService.uploadAll(
                List.of(
                        new TestFileSource("a.txt", contentA),
                        new TestFileSource("b.txt", contentB)
                ),
                StorageType.MEMORY, "bucket");

        assertTrue(result.allSucceeded());

        FileMetadata metaA = result.succeeded().stream()
                .filter(m -> "a.txt".equals(m.name())).findFirst().orElseThrow();
        FileMetadata metaB = result.succeeded().stream()
                .filter(m -> "b.txt".equals(m.name())).findFirst().orElseThrow();

        assertNotEquals(metaA.key(), metaB.key());

        try (InputStream is = downloadService.download(metaA.key()).content()) {
            assertArrayEquals(contentA, is.readAllBytes());
        }
        try (InputStream is = downloadService.download(metaB.key()).content()) {
            assertArrayEquals(contentB, is.readAllBytes());
        }
    }

    @Test
    void uploadAll_metadataPreserved() {
        BatchUploadResult result = uploadService.uploadAll(
                List.of(new TestFileSource("test.txt", "data".getBytes())),
                StorageType.MEMORY, "bucket");

        FileMetadata meta = result.succeeded().get(0);
        assertNotNull(meta.key());
        assertEquals("test.txt", meta.name());
        assertEquals(4, meta.size());
        assertNotNull(meta.checksum());
        assertEquals("text/plain", meta.format().mimeType());
        assertNotNull(meta.location());
    }

    @Test
    void uploadAll_partialFailure_validFilesStillUploaded() {
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
        assertEquals("small.txt", result.succeeded().get(0).name());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().containsKey("big.txt"));
        assertEquals(2, result.totalRequested());
        assertEquals(1, memoryStorage.size());
    }

    @Test
    void uploadAll_invalidFilename_failedEntry() {
        BatchUploadResult result = uploadService.uploadAll(
                List.of(
                        new TestFileSource("good.txt", "ok".getBytes()),
                        new TestFileSource("../evil.txt", "bad".getBytes())
                ),
                StorageType.MEMORY, "bucket");

        assertFalse(result.allSucceeded());
        assertEquals(1, result.succeeded().size());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().containsKey("../evil.txt"));
    }

    @Test
    void uploadAll_emptyCollection() {
        BatchUploadResult result = uploadService.uploadAll(
                List.of(), StorageType.MEMORY, "bucket");

        assertTrue(result.allSucceeded());
        assertEquals(0, result.totalRequested());
        assertEquals(0, memoryStorage.size());
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
        assertEquals(result.succeeded().get(0).key(), result.succeeded().get(1).key());
        assertEquals(result.succeeded().get(0).checksum(), result.succeeded().get(1).checksum());
        assertEquals(1, memoryStorage.size());
    }
}
