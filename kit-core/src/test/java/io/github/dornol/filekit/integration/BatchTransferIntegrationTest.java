package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageException;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertEquals(0, result.failed().size());
            assertEquals(2, result.totalRequested());
            assertEquals(2, storageA.size());
            assertEquals(2, storageB.size());
        }

        @Test
        void copyAll_contentPreserved() throws IOException {
            byte[] contentA = "copy A".getBytes();
            byte[] contentB = "copy B".getBytes();
            FileMetadata m1 = uploadService.upload(
                    new TestFileSource("a.txt", contentA), StorageType.A, "bucket");
            FileMetadata m2 = uploadService.upload(
                    new TestFileSource("b.txt", contentB), StorageType.A, "bucket");

            BatchTransferResult result = transferService.copyAll(
                    List.of(m1.key(), m2.key()), StorageType.B, "bucket-b");

            for (FileMetadata copied : result.succeeded()) {
                try (InputStream is = downloadService.download(copied.key()).content()) {
                    byte[] expected = copied.name().equals("a.txt") ? contentA : contentB;
                    assertArrayEquals(expected, is.readAllBytes());
                }
            }
        }

        @Test
        void copyAll_metadataPreserved() throws IOException {
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            BatchTransferResult result = transferService.copyAll(
                    List.of(source.key()), StorageType.B, "bucket-b");

            FileMetadata copied = result.succeeded().get(0);
            assertNotEquals(source.key(), copied.key());
            assertEquals(source.name(), copied.name());
            assertEquals(source.checksum(), copied.checksum());
            assertEquals(source.format(), copied.format());
            assertEquals(source.size(), copied.size());
        }

        @Test
        void copyAll_sourceStillAccessible() throws IOException {
            byte[] content = "still here".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.A, "bucket");

            transferService.copyAll(List.of(source.key()), StorageType.B, "bucket-b");

            try (InputStream is = downloadService.download(source.key()).content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
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
            assertEquals(2, result.totalRequested());
        }

        @Test
        void copyAll_emptyCollection() {
            BatchTransferResult result = transferService.copyAll(
                    List.of(), StorageType.B, "bucket-b");

            assertTrue(result.allSucceeded());
            assertEquals(0, result.totalRequested());
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
            assertEquals(0, result.failed().size());
            assertEquals(0, storageA.size());
            assertEquals(2, storageB.size());
        }

        @Test
        void moveAll_sourceGone() throws IOException {
            byte[] content = "move me".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.A, "bucket");
            String sourceKey = source.key();

            BatchTransferResult result = transferService.moveAll(
                    List.of(sourceKey), StorageType.B, "bucket-b");

            // target accessible
            try (InputStream is = downloadService.download(result.succeeded().get(0).key()).content()) {
                assertArrayEquals(content, is.readAllBytes());
            }

            // source gone
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> downloadService.download(sourceKey));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void moveAll_metadataPreserved() throws IOException {
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.A, "bucket");

            BatchTransferResult result = transferService.moveAll(
                    List.of(source.key()), StorageType.B, "bucket-b");

            FileMetadata moved = result.succeeded().get(0);
            assertNotEquals(source.key(), moved.key());
            assertEquals(source.name(), moved.name());
            assertEquals(source.checksum(), moved.checksum());
            assertEquals(source.format(), moved.format());
            assertEquals(source.size(), moved.size());
        }

        @Test
        void moveAll_partialFailure_validFilesStillMoved() throws IOException {
            FileMetadata m1 = uploadService.upload(
                    new TestFileSource("a.txt", "aaa".getBytes()), StorageType.A, "bucket");

            BatchTransferResult result = transferService.moveAll(
                    List.of(m1.key(), "non-existent-key"), StorageType.B, "bucket-b");

            assertFalse(result.allSucceeded());
            assertEquals(1, result.succeeded().size());
            assertEquals(1, result.failed().size());
            assertEquals(0, storageA.size());
            assertEquals(1, storageB.size());
        }

        @Test
        void moveAll_emptyCollection() {
            BatchTransferResult result = transferService.moveAll(
                    List.of(), StorageType.B, "bucket-b");

            assertTrue(result.allSucceeded());
            assertEquals(0, result.totalRequested());
        }
    }
}
