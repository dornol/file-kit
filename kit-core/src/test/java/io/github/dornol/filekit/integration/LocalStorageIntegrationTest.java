package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.local.LocalFileStorage;
import io.github.dornol.filekit.storage.local.ObjectKeyStrategy;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests using LocalFileStorage with real filesystem.
 */
class LocalStorageIntegrationTest {

    enum StorageType { LOCAL }

    @TempDir
    Path tempDir;

    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;
    private FileDeleteService deleteService;

    @BeforeEach
    void setUp() {
        LocalFileStorage localStorage = new LocalFileStorage(
                tempDir, StorageType.LOCAL, ObjectKeyStrategy.flat());
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(localStorage));

        uploadService = FileUploadService.builder(
                new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("application/octet-stream", "bin", "application"),
                storageResolver).build();
        downloadService = FileDownloadService.builder(metadataRepository, storageResolver).build();
        deleteService = FileDeleteService.builder(metadataRepository, storageResolver).build();
    }

    @Nested
    class UploadAndDownload {

        @Test
        void fileWrittenToDisk_andReadBack() throws IOException {
            byte[] content = "local file content".getBytes();
            FileSource source = new TestFileSource("data.bin", content);

            FileMetadata meta = uploadService.upload(source, StorageType.LOCAL, "uploads");

            // Verify file exists on disk
            Path bucket = tempDir.resolve("uploads");
            assertTrue(Files.exists(bucket), "Bucket directory should exist");

            long fileCount = Files.list(bucket).count();
            assertEquals(1, fileCount, "Exactly one file in bucket");

            // Download and verify content
            DownloadResult result = downloadService.download(meta.key());
            try (InputStream is = result.content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }

        @Test
        void multipleFiles_inDifferentBuckets() throws IOException {
            FileMetadata meta1 = uploadService.upload(
                    new TestFileSource("a.bin", "aaa".getBytes()), StorageType.LOCAL, "bucket-a");
            FileMetadata meta2 = uploadService.upload(
                    new TestFileSource("b.bin", "bbb".getBytes()), StorageType.LOCAL, "bucket-b");

            assertTrue(Files.exists(tempDir.resolve("bucket-a")));
            assertTrue(Files.exists(tempDir.resolve("bucket-b")));

            try (InputStream is1 = downloadService.download(meta1.key()).content()) {
                assertArrayEquals("aaa".getBytes(), is1.readAllBytes());
            }
            try (InputStream is2 = downloadService.download(meta2.key()).content()) {
                assertArrayEquals("bbb".getBytes(), is2.readAllBytes());
            }
        }

        @Test
        void resolveUri_returnsFileUri() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.bin", "data".getBytes()), StorageType.LOCAL, "bucket");

            String uri = downloadService.resolveUri(meta.key());
            assertTrue(uri.startsWith("file:"));
        }
    }

    @Nested
    class HashPrefixedStrategy {

        @Test
        void filesOrganizedInSubdirectories() throws IOException {
            LocalFileStorage hashedStorage = new LocalFileStorage(
                    tempDir.resolve("hashed"), StorageType.LOCAL, ObjectKeyStrategy.hashPrefixed(2));
            FileStorageResolver resolver = new FileStorageResolver(List.of(hashedStorage));
            FileUploadService hashedUploadService = FileUploadService.builder(
                    new Sha256ChecksumCalculator(), metadataRepository,
                    is -> new FileFormat("text/plain", "txt", "text"),
                    resolver).build();

            FileMetadata meta = hashedUploadService.upload(
                    new TestFileSource("doc.txt", "hash me".getBytes()), StorageType.LOCAL, "data");

            // Object key should contain subdirectory separators
            assertTrue(meta.location().objectKey().contains("/"),
                    "Hash-prefixed key should have subdirectories");

            // File should exist at the correct path
            Path filePath = tempDir.resolve("hashed").resolve("data")
                    .resolve(meta.location().objectKey());
            assertTrue(Files.exists(filePath));
        }
    }

    @Nested
    class ValidationOnFilesystem {

        @Test
        void pathTraversalFilename_rejected_noFileCreated() {
            FileSource source = new TestFileSource("../escape.txt", "evil".getBytes());

            assertThrows(FileStorageException.class,
                    () -> uploadService.upload(source, StorageType.LOCAL, "bucket"));

            // Ensure no file was created outside of the expected directory
            assertFalse(Files.exists(tempDir.resolve("escape.txt")));
        }

        @Test
        void backslashFilename_rejected() {
            FileSource source = new TestFileSource("sub\\file.txt", "data".getBytes());

            assertThrows(FileStorageException.class,
                    () -> uploadService.upload(source, StorageType.LOCAL, "bucket"));
        }

        @Test
        void downloadNonExistent_throwsFileNotFound() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> downloadService.download("no-such-key"));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }
    }

    @Nested
    class CallbackAndRollback {

        @Test
        void callbackFailure_fileRemovedFromDisk() {
            FileSource source = new TestFileSource("temp.bin", "temporary".getBytes());

            assertThrows(FileStorageException.class,
                    () -> uploadService.upload(source, StorageType.LOCAL, "bucket",
                            metadata -> {
                                throw new RuntimeException("rollback");
                            }));

            // Verify no files remain in the bucket
            Path bucket = tempDir.resolve("bucket");
            if (Files.exists(bucket)) {
                try (var files = Files.list(bucket)) {
                    assertEquals(0, files.count(),
                            "File should be cleaned up after callback failure");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Test
        void callbackSuccess_filePersistsOnDisk() throws IOException {
            FileSource source = new TestFileSource("keep.bin", "persistent".getBytes());

            FileMetadata meta = uploadService.upload(source, StorageType.LOCAL, "bucket",
                    metadata -> { /* no-op callback */ });

            assertNotNull(metadataRepository.findByKey(meta.key()));
            Path bucket = tempDir.resolve("bucket");
            try (var files = Files.list(bucket)) {
                assertEquals(1, files.count());
            }
        }
    }

    @Nested
    class Deduplication {

        @Test
        void duplicateContent_reusesExistingFile() throws IOException {
            byte[] content = "duplicate".getBytes();

            FileMetadata first = uploadService.upload(
                    new TestFileSource("first.bin", content), StorageType.LOCAL, "bucket");
            FileMetadata second = uploadService.upload(
                    new TestFileSource("second.bin", content), StorageType.LOCAL, "bucket");

            assertEquals(first.key(), second.key());

            // Only one file on disk
            Path bucket = tempDir.resolve("bucket");
            try (var files = Files.list(bucket)) {
                assertEquals(1, files.count());
            }
        }
    }

    // ── Delete on filesystem ──────────────────────────────────────────

    @Nested
    class DeleteOnFilesystem {

        @Test
        void delete_removesFileFromDiskAndMetadata() throws IOException {
            FileSource source = new TestFileSource("removable.bin", "remove me".getBytes());
            FileMetadata meta = uploadService.upload(source, StorageType.LOCAL, "bucket");

            Path bucket = tempDir.resolve("bucket");
            try (var files = Files.list(bucket)) {
                assertEquals(1, files.count());
            }

            deleteService.delete(meta.key());

            try (var files = Files.list(bucket)) {
                assertEquals(0, files.count(), "File should be removed from disk");
            }
            assertEquals(0, metadataRepository.count());
        }

        @Test
        void delete_thenDownload_throwsNotFound() throws IOException {
            FileSource source = new TestFileSource("gone.bin", "goodbye".getBytes());
            FileMetadata meta = uploadService.upload(source, StorageType.LOCAL, "bucket");

            deleteService.delete(meta.key());

            assertThrows(FileStorageException.class,
                    () -> downloadService.download(meta.key()));
        }
    }

}
