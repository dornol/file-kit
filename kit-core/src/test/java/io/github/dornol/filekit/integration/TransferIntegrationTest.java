package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageException;
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
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for file copy and move operations between storage backends.
 */
class TransferIntegrationTest {

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
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(storageA, storageB));

        uploadService = FileUploadService.builder(new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"), storageResolver).build();
        downloadService = FileDownloadService.builder(metadataRepository, storageResolver).build();
        transferService = FileTransferService.builder(metadataRepository, storageResolver).build();
    }

    @Nested
    class Copy {

        @Test
        void copy_bothStoragesHaveContent() throws IOException {
            byte[] content = "copy me".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.A, "bucket-a");

            FileMetadata copied = transferService.copy(source.key(), StorageType.B, "bucket-b");

            try (InputStream isA = downloadService.download(source.key()).content()) {
                assertArrayEquals(content, isA.readAllBytes());
            }
            try (InputStream isB = downloadService.download(copied.key()).content()) {
                assertArrayEquals(content, isB.readAllBytes());
            }
        }

        @Test
        void copy_sourceStillAccessible() throws IOException {
            byte[] content = "still here".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.A, "bucket-a");

            transferService.copy(source.key(), StorageType.B, "bucket-b");

            DownloadResult result = downloadService.download(source.key());
            try (InputStream is = result.content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }

        @Test
        void copy_metadataPreserved() throws IOException {
            byte[] content = "metadata check".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("original.txt", content), StorageType.A, "bucket-a");

            FileMetadata copied = transferService.copy(source.key(), StorageType.B, "bucket-b");

            assertNotEquals(source.key(), copied.key());
            assertEquals(source.name(), copied.name());
            assertEquals(source.checksum(), copied.checksum());
            assertEquals(source.format(), copied.format());
            assertEquals(source.size(), copied.size());
            assertNotEquals(source.location(), copied.location());
        }
    }

    @Nested
    class Move {

        @Test
        void move_sourceGone() throws IOException {
            byte[] content = "move me".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.A, "bucket-a");
            String sourceKey = source.key();

            FileMetadata moved = transferService.move(sourceKey, StorageType.B, "bucket-b");

            // target accessible
            try (InputStream is = downloadService.download(moved.key()).content()) {
                assertArrayEquals(content, is.readAllBytes());
            }

            // source gone
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> downloadService.download(sourceKey));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void move_metadataPreserved() throws IOException {
            byte[] content = "move metadata".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("movable.txt", content), StorageType.A, "bucket-a");

            FileMetadata moved = transferService.move(source.key(), StorageType.B, "bucket-b");

            assertNotEquals(source.key(), moved.key());
            assertEquals(source.name(), moved.name());
            assertEquals(source.checksum(), moved.checksum());
            assertEquals(source.format(), moved.format());
            assertEquals(source.size(), moved.size());

            assertNotNull(metadataRepository.findByKey(moved.key()));
        }
    }
}
