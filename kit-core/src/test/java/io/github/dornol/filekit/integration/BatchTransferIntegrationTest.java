package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.transfer.BatchTransferResult;
import io.github.dornol.filekit.transfer.FileTransferService;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchTransferIntegrationTest {

    enum StorageType { A, B }

    private InMemoryFileStorage storageA;
    private InMemoryFileStorage storageB;
    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;
    private FileTransferService transferService;

    @BeforeEach
    void setUp() {
        storageA = new InMemoryFileStorage(StorageType.A);
        storageB = new InMemoryFileStorage(StorageType.B);
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver resolver = new FileStorageResolver(List.of(storageA, storageB));

        uploadService = FileUploadService.builder(new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"), resolver).build();
        downloadService = FileDownloadService.builder(metadataRepository, resolver).build();
        transferService = FileTransferService.builder(metadataRepository, resolver).build();
    }

    @Nested
    class CopyAll {

        @Test
        void copyAll_allSucceed() throws IOException {
            FileMetadata m1 = uploadService.upload(
                    new TestFileSource("a.txt", "aaa".getBytes()), StorageType.A, "bucket");
            FileMetadata m2 = uploadService.upload(
                    new TestFileSource("b.txt", "bbb".getBytes()), StorageType.A, "bucket");

            BatchTransferResult result = transferService.copyAll(
                    List.of(m1.key(), m2.key()), StorageType.B, "bucket-b");

            assertTrue(result.allSucceeded());
            assertEquals(2, result.succeeded().size());
            assertEquals(2, storageA.size());
            assertEquals(2, storageB.size());
        }

        @Test
        void copyAll_partialFailure() throws IOException {
            FileMetadata m1 = uploadService.upload(
                    new TestFileSource("a.txt", "aaa".getBytes()), StorageType.A, "bucket");

            BatchTransferResult result = transferService.copyAll(
                    List.of(m1.key(), "non-existent-key"), StorageType.B, "bucket-b");

            assertFalse(result.allSucceeded());
            assertEquals(1, result.succeeded().size());
            assertEquals(1, result.failed().size());
            assertTrue(result.failed().containsKey("non-existent-key"));
        }

        @Test
        void copyAll_contentPreserved() throws IOException {
            byte[] content = "copy me".getBytes();
            FileMetadata m1 = uploadService.upload(
                    new TestFileSource("a.txt", content), StorageType.A, "bucket");

            BatchTransferResult result = transferService.copyAll(
                    List.of(m1.key()), StorageType.B, "bucket-b");

            try (InputStream is = downloadService.download(result.succeeded().get(0).key()).content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }
    }

    @Nested
    class MoveAll {

        @Test
        void moveAll_allSucceed() throws IOException {
            FileMetadata m1 = uploadService.upload(
                    new TestFileSource("a.txt", "aaa".getBytes()), StorageType.A, "bucket");
            FileMetadata m2 = uploadService.upload(
                    new TestFileSource("b.txt", "bbb".getBytes()), StorageType.A, "bucket");

            BatchTransferResult result = transferService.moveAll(
                    List.of(m1.key(), m2.key()), StorageType.B, "bucket-b");

            assertTrue(result.allSucceeded());
            assertEquals(2, result.succeeded().size());
            assertEquals(0, storageA.size());
            assertEquals(2, storageB.size());
        }

        @Test
        void moveAll_partialFailure() throws IOException {
            FileMetadata m1 = uploadService.upload(
                    new TestFileSource("a.txt", "aaa".getBytes()), StorageType.A, "bucket");

            BatchTransferResult result = transferService.moveAll(
                    List.of(m1.key(), "non-existent-key"), StorageType.B, "bucket-b");

            assertFalse(result.allSucceeded());
            assertEquals(1, result.succeeded().size());
            assertEquals(1, result.failed().size());
        }

        @Test
        void moveAll_empty() {
            BatchTransferResult result = transferService.moveAll(
                    List.of(), StorageType.B, "bucket-b");

            assertTrue(result.allSucceeded());
            assertEquals(0, result.totalRequested());
        }
    }
}
