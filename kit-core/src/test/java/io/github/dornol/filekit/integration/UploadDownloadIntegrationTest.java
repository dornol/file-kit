package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.upload.FileUploadService;
import io.github.dornol.filekit.upload.UploadCallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests that exercise the full upload → download flow
 * using real implementations (no mocks).
 */
class UploadDownloadIntegrationTest {

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
        ChecksumCalculator checksumCalculator = new Sha256ChecksumCalculator();
        FileFormatExtractor formatExtractor = is -> new FileFormat("text/plain", "txt", "text");
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(memoryStorage));

        uploadService = new FileUploadService(checksumCalculator, metadataRepository,
                formatExtractor, storageResolver);
        downloadService = new FileDownloadService(metadataRepository, storageResolver);
        deleteService = new FileDeleteService(metadataRepository, storageResolver);
    }

    // ── Upload → Download full flow ──────────────────────────────────

    @Nested
    class FullFlow {

        @Test
        void uploadAndDownload_contentPreserved() throws IOException {
            byte[] content = "Hello, file-kit!".getBytes();
            FileSource source = new TestFileSource("greeting.txt", content);

            FileMetadata uploaded = uploadService.upload(source, StorageType.MEMORY, "docs");

            assertNotNull(uploaded.key());
            assertEquals("greeting.txt", uploaded.name());
            assertEquals(content.length, uploaded.size());

            DownloadResult result = downloadService.download(uploaded.key());
            assertEquals(uploaded, result.metadata());
            try (InputStream is = result.content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }

        @Test
        void uploadMultipleFiles_eachDownloadable() throws IOException {
            byte[] content1 = "file one".getBytes();
            byte[] content2 = "file two".getBytes();

            FileMetadata meta1 = uploadService.upload(
                    new TestFileSource("one.txt", content1), StorageType.MEMORY, "bucket");
            FileMetadata meta2 = uploadService.upload(
                    new TestFileSource("two.txt", content2), StorageType.MEMORY, "bucket");

            assertNotEquals(meta1.key(), meta2.key());

            try (InputStream is1 = downloadService.download(meta1.key()).content()) {
                assertArrayEquals(content1, is1.readAllBytes());
            }
            try (InputStream is2 = downloadService.download(meta2.key()).content()) {
                assertArrayEquals(content2, is2.readAllBytes());
            }
        }

        @Test
        void uploadAndResolveUri() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()), StorageType.MEMORY, "bucket");

            String uri = downloadService.resolveUri(meta.key());

            assertNotNull(uri);
            assertTrue(uri.startsWith("memory://"));
            assertTrue(uri.contains("bucket"));
        }
    }

    // ── Deduplication ────────────────────────────────────────────────

    @Nested
    class Deduplication {

        @Test
        void sameContent_returnsSameMetadata() throws IOException {
            byte[] content = "duplicate content".getBytes();

            FileMetadata first = uploadService.upload(
                    new TestFileSource("first.txt", content), StorageType.MEMORY, "bucket");
            FileMetadata second = uploadService.upload(
                    new TestFileSource("second.txt", content), StorageType.MEMORY, "bucket");

            assertEquals(first.key(), second.key());
            assertEquals(first.checksum(), second.checksum());
            assertEquals(1, memoryStorage.size(), "Only one file should be stored");
        }

        @Test
        void differentContent_createsSeparateEntries() throws IOException {
            FileMetadata first = uploadService.upload(
                    new TestFileSource("a.txt", "content A".getBytes()), StorageType.MEMORY, "bucket");
            FileMetadata second = uploadService.upload(
                    new TestFileSource("b.txt", "content B".getBytes()), StorageType.MEMORY, "bucket");

            assertNotEquals(first.key(), second.key());
            assertNotEquals(first.checksum(), second.checksum());
            assertEquals(2, memoryStorage.size());
        }
    }

    // ── Checksum integrity ───────────────────────────────────────────

    @Nested
    class ChecksumIntegrity {

        @Test
        void checksumIsConsistent() throws IOException {
            byte[] content = "consistent content".getBytes();

            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.MEMORY, "bucket");

            Sha256ChecksumCalculator calc = new Sha256ChecksumCalculator();
            assertEquals(calc.checksum(content), meta.checksum());
        }

        @Test
        void differentContent_differentChecksum() throws IOException {
            FileMetadata meta1 = uploadService.upload(
                    new TestFileSource("a.txt", "AAA".getBytes()), StorageType.MEMORY, "bucket");
            FileMetadata meta2 = uploadService.upload(
                    new TestFileSource("b.txt", "BBB".getBytes()), StorageType.MEMORY, "bucket");

            assertNotEquals(meta1.checksum(), meta2.checksum());
        }
    }

    // ── Validation in full flow ──────────────────────────────────────

    @Nested
    class ValidationInFlow {

        @Test
        void pathTraversalFilename_rejectedBeforeUpload() {
            FileSource source = new TestFileSource("../etc/passwd", "evil".getBytes());

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> uploadService.upload(source, StorageType.MEMORY, "bucket"));

            assertEquals(FileStorageException.INVALID_FILENAME, ex.getMessageKey());
            assertEquals(0, memoryStorage.size(), "No file should be stored");
        }

        @Test
        void backslashInFilename_rejectedBeforeUpload() {
            FileSource source = new TestFileSource("path\\file.txt", "data".getBytes());

            assertThrows(FileStorageException.class,
                    () -> uploadService.upload(source, StorageType.MEMORY, "bucket"));

            assertEquals(0, memoryStorage.size());
        }

        @Test
        void tooLongFilename_rejected() {
            String longName = "a".repeat(201) + ".txt";
            FileSource source = new TestFileSource(longName, "data".getBytes());

            assertThrows(FileStorageException.class,
                    () -> uploadService.upload(source, StorageType.MEMORY, "bucket"));

            assertEquals(0, memoryStorage.size());
        }

        @Test
        void nullFilename_generatesNameAndSucceeds() throws IOException {
            FileSource source = new TestFileSource(null, "data".getBytes());

            FileMetadata meta = uploadService.upload(source, StorageType.MEMORY, "bucket");

            assertNotNull(meta.name());
            assertTrue(meta.name().endsWith(".txt"));
            assertEquals(1, memoryStorage.size());
        }

        @Test
        void emptyFile_uploadsSuccessfully() throws IOException {
            FileSource source = new TestFileSource("empty.txt", new byte[0]);

            FileMetadata meta = uploadService.upload(source, StorageType.MEMORY, "bucket");

            assertEquals(0, meta.size());
        }
    }

    // ── Size limit in full flow ──────────────────────────────────────

    @Nested
    class SizeLimitInFlow {

        @Test
        void withinLimit_succeeds() throws IOException {
            FileUploadService limited = new FileUploadService(
                    new Sha256ChecksumCalculator(), metadataRepository,
                    is -> new FileFormat("text/plain", "txt", "text"),
                    new FileStorageResolver(List.of(memoryStorage)), 100);

            FileSource source = new TestFileSource("small.txt", "hi".getBytes());
            FileMetadata meta = limited.upload(source, StorageType.MEMORY, "bucket");

            assertNotNull(meta);
        }

        @Test
        void exceedsLimit_rejected() {
            FileUploadService limited = new FileUploadService(
                    new Sha256ChecksumCalculator(), metadataRepository,
                    is -> new FileFormat("text/plain", "txt", "text"),
                    new FileStorageResolver(List.of(memoryStorage)), 5);

            FileSource source = new TestFileSource("big.txt", "this is too long".getBytes());

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> limited.upload(source, StorageType.MEMORY, "bucket"));
            assertEquals(FileStorageException.FILE_TOO_LARGE, ex.getMessageKey());
        }
    }

    // ── Callback in full flow ────────────────────────────────────────

    @Nested
    class CallbackInFlow {

        @Test
        void callbackReceivesMetadata_thenSaved() throws IOException {
            AtomicReference<FileMetadata> captured = new AtomicReference<>();

            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "data".getBytes()),
                    StorageType.MEMORY, "bucket",
                    captured::set);

            assertNotNull(captured.get());
            assertEquals(meta.key(), captured.get().key());
            assertEquals(meta.name(), captured.get().name());
            assertNotNull(metadataRepository.findByKey(meta.key()));
        }

        @Test
        void callbackFails_fileDeletedAndNotSaved() {
            UploadCallback failingCallback = metadata -> {
                throw new RuntimeException("business rule violation");
            };

            assertThrows(FileStorageException.class,
                    () -> uploadService.upload(
                            new TestFileSource("file.txt", "data".getBytes()),
                            StorageType.MEMORY, "bucket",
                            failingCallback));

            assertEquals(0, memoryStorage.size(), "File should be cleaned up");
            assertEquals(0, metadataRepository.count(), "No metadata should be saved");
        }
    }

    // ── Delete flow ───────────────────────────────────────────────────

    @Nested
    class DeleteFlow {

        @Test
        void uploadAndDelete_fileRemovedFromStorageAndRepository() throws IOException {
            byte[] content = "to be deleted".getBytes();
            FileSource source = new TestFileSource("delete-me.txt", content);

            FileMetadata uploaded = uploadService.upload(source, StorageType.MEMORY, "docs");
            assertEquals(1, memoryStorage.size());
            assertNotNull(metadataRepository.findByKey(uploaded.key()));

            deleteService.delete(uploaded.key());

            assertEquals(0, memoryStorage.size());
            assertEquals(0, metadataRepository.count());
        }

        @Test
        void deleteNonExistentKey_throwsFileNotFound() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> deleteService.delete("non-existent-key"));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void uploadMultiple_deleteOne_otherRemains() throws IOException {
            FileMetadata meta1 = uploadService.upload(
                    new TestFileSource("a.txt", "content A".getBytes()), StorageType.MEMORY, "bucket");
            FileMetadata meta2 = uploadService.upload(
                    new TestFileSource("b.txt", "content B".getBytes()), StorageType.MEMORY, "bucket");

            assertEquals(2, memoryStorage.size());

            deleteService.delete(meta1.key());

            assertEquals(1, memoryStorage.size());
            assertNotNull(metadataRepository.findByKey(meta2.key()));

            // Download remaining file still works
            DownloadResult result = downloadService.download(meta2.key());
            try (InputStream is = result.content()) {
                assertArrayEquals("content B".getBytes(), is.readAllBytes());
            }
        }

        @Test
        void deleteAndReupload_samContentGetsNewEntry() throws IOException {
            byte[] content = "reuploadable".getBytes();

            FileMetadata first = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.MEMORY, "bucket");
            deleteService.delete(first.key());

            assertEquals(0, memoryStorage.size());

            // Re-upload same content — should get a new key since metadata was deleted
            FileMetadata second = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.MEMORY, "bucket");

            assertNotEquals(first.key(), second.key());
            assertEquals(1, memoryStorage.size());
        }
    }

    // ── Download non-existent file ───────────────────────────────────

    @Nested
    class DownloadErrors {

        @Test
        void downloadNonExistentKey_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> downloadService.download("non-existent-key"));

            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void resolveUriNonExistentKey_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> downloadService.resolveUri("non-existent-key"));

            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }
    }

}
