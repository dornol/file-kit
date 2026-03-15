package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.local.LocalFileStorage;
import io.github.dornol.filekit.storage.local.ObjectKeyStrategy;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
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

    private UploadDownloadIntegrationTest.InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;

    @BeforeEach
    void setUp() {
        LocalFileStorage localStorage = new LocalFileStorage(
                tempDir, StorageType.LOCAL, ObjectKeyStrategy.flat());
        metadataRepository = new UploadDownloadIntegrationTest.InMemoryMetadataRepository();
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(localStorage));

        uploadService = new FileUploadService(
                new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("application/octet-stream", "bin", "application"),
                storageResolver);
        downloadService = new FileDownloadService(metadataRepository, storageResolver);
    }

    @Nested
    class UploadAndDownload {

        @Test
        void fileWrittenToDisk_andReadBack() throws IOException {
            byte[] content = "local file content".getBytes();
            FileSource source = testSource("data.bin", content);

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
                    testSource("a.bin", "aaa".getBytes()), StorageType.LOCAL, "bucket-a");
            FileMetadata meta2 = uploadService.upload(
                    testSource("b.bin", "bbb".getBytes()), StorageType.LOCAL, "bucket-b");

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
                    testSource("file.bin", "data".getBytes()), StorageType.LOCAL, "bucket");

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
            FileUploadService hashedUploadService = new FileUploadService(
                    new Sha256ChecksumCalculator(), metadataRepository,
                    is -> new FileFormat("text/plain", "txt", "text"),
                    resolver);

            FileMetadata meta = hashedUploadService.upload(
                    testSource("doc.txt", "hash me".getBytes()), StorageType.LOCAL, "data");

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
            FileSource source = testSource("../escape.txt", "evil".getBytes());

            assertThrows(FileStorageException.class,
                    () -> uploadService.upload(source, StorageType.LOCAL, "bucket"));

            // Ensure no file was created outside of the expected directory
            assertFalse(Files.exists(tempDir.resolve("escape.txt")));
        }

        @Test
        void backslashFilename_rejected() {
            FileSource source = testSource("sub\\file.txt", "data".getBytes());

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
            FileSource source = testSource("temp.bin", "temporary".getBytes());

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
            FileSource source = testSource("keep.bin", "persistent".getBytes());

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
                    testSource("first.bin", content), StorageType.LOCAL, "bucket");
            FileMetadata second = uploadService.upload(
                    testSource("second.bin", content), StorageType.LOCAL, "bucket");

            assertEquals(first.key(), second.key());

            // Only one file on disk
            Path bucket = tempDir.resolve("bucket");
            try (var files = Files.list(bucket)) {
                assertEquals(1, files.count());
            }
        }
    }

    // ── Helper ───────────────────────────────────────────────────────

    private static FileSource testSource(String filename, byte[] content) {
        return new FileSource() {
            @Override
            public String getOriginalFilename() {
                return filename;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public long getSize() {
                return content.length;
            }

            @Override
            public boolean isEmpty() {
                return content.length == 0;
            }
        };
    }
}
