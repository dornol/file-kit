package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.scan.ScanResult;
import io.github.dornol.filekit.scan.VirusScanner;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {

    enum StorageType { LOCAL }

    ChecksumCalculator checksumCalculator = mock(ChecksumCalculator.class);
    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileFormatExtractor formatExtractor = mock(FileFormatExtractor.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);
    FileStorage fileStorage = mock(FileStorage.class);
    FileSource fileSource = mock(FileSource.class);

    FileUploadService service;
    FileUploadService serviceLimited;

    @BeforeEach
    void setUp() {
        service = new FileUploadService(checksumCalculator, metadataRepository,
                formatExtractor, storageResolver);
        serviceLimited = new FileUploadService(checksumCalculator, metadataRepository,
                formatExtractor, storageResolver, 10);
    }

    // ── Constructor validation ───────────────────────────────────────

    @Nested
    class ConstructorValidation {

        @Test
        void nullChecksumCalculator_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileUploadService(null, metadataRepository, formatExtractor, storageResolver));
        }

        @Test
        void nullMetadataRepository_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileUploadService(checksumCalculator, null, formatExtractor, storageResolver));
        }

        @Test
        void nullFormatExtractor_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileUploadService(checksumCalculator, metadataRepository, null, storageResolver));
        }

        @Test
        void nullStorageResolver_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileUploadService(checksumCalculator, metadataRepository, formatExtractor, null));
        }

        @Test
        void nullChecksumCalculator_withMaxSize_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileUploadService(null, metadataRepository, formatExtractor, storageResolver, 100));
        }
    }

    // ── Parameter validation ─────────────────────────────────────────

    @Nested
    class ParameterValidation {

        @Test
        void nullFileSource_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.upload(null, StorageType.LOCAL, "bucket"));
        }

        @Test
        void nullStorageType_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.upload(fileSource, null, "bucket"));
        }

        @Test
        void nullBucket_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, null));
        }

        @Test
        void nullFileSource_withCallback_throws() {
            UploadCallback callback = mock(UploadCallback.class);
            assertThrows(NullPointerException.class,
                    () -> service.upload(null, StorageType.LOCAL, "bucket", callback));
        }

        @Test
        void nullStorageType_withCallback_throws() {
            UploadCallback callback = mock(UploadCallback.class);
            assertThrows(NullPointerException.class,
                    () -> service.upload(fileSource, null, "bucket", callback));
        }

        @Test
        void nullBucket_withCallback_throws() {
            UploadCallback callback = mock(UploadCallback.class);
            assertThrows(NullPointerException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, null, callback));
        }
    }

    // ── Filename validation ──────────────────────────────────────────

    @Nested
    class FilenameValidation {

        @ParameterizedTest
        @ValueSource(strings = {"../etc/passwd", "..\\etc\\passwd", "foo/../bar", "../../escape"})
        void pathTraversal_throws(String filename) {
            when(fileSource.getOriginalFilename()).thenReturn(filename);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.INVALID_FILENAME, ex.getMessageKey());
        }

        @Test
        void filenameWithForwardSlash_throws() {
            when(fileSource.getOriginalFilename()).thenReturn("path/file.txt");

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.INVALID_FILENAME, ex.getMessageKey());
        }

        @Test
        void filenameWithBackslash_throws() {
            when(fileSource.getOriginalFilename()).thenReturn("path\\file.txt");

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.INVALID_FILENAME, ex.getMessageKey());
        }

        @Test
        void filenameWithDoubleDot_throws() {
            when(fileSource.getOriginalFilename()).thenReturn("foo..bar.txt");

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.INVALID_FILENAME, ex.getMessageKey());
        }

        @Test
        void filenameTooLong_throws() {
            when(fileSource.getOriginalFilename()).thenReturn("a".repeat(201) + ".txt");

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.INVALID_FILENAME, ex.getMessageKey());
        }

        @Test
        void filenameExactly200_allowed() throws IOException {
            String name = "a".repeat(196) + ".txt"; // 200 chars
            setupSuccessfulUpload(name);

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket");
            assertNotNull(result);
            assertEquals(name, result.name());
        }

        @Test
        void filenameExactly201_throws() {
            String name = "a".repeat(197) + ".txt"; // 201 chars
            when(fileSource.getOriginalFilename()).thenReturn(name);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.INVALID_FILENAME, ex.getMessageKey());
        }

        @Test
        void nullFilename_allowed_generatesName() throws IOException {
            byte[] content = "hello".getBytes();
            FileFormat format = new FileFormat("text/plain", "txt", "text");
            FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

            when(fileSource.getOriginalFilename()).thenReturn(null);
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
            when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
            when(formatExtractor.extract(any())).thenReturn(format);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.upload(any())).thenReturn(location);
            when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket");

            assertNotNull(result);
            assertTrue(result.name().endsWith(".txt"), "Generated name should end with extension");
            assertNotNull(result.key());
            assertTrue(result.name().contains(result.key()),
                    "Generated name should contain the key");
        }

        @Test
        void validFilename_withKorean_allowed() throws IOException {
            setupSuccessfulUpload("보고서.pdf");

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket");
            assertEquals("보고서.pdf", result.name());
        }

        @Test
        void validFilename_withSpaces_allowed() throws IOException {
            setupSuccessfulUpload("my file name.txt");

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket");
            assertEquals("my file name.txt", result.name());
        }

        @Test
        void validFilename_withSingleDot_allowed() throws IOException {
            setupSuccessfulUpload("file.backup.txt");

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket");
            assertEquals("file.backup.txt", result.name());
        }

        @Test
        void filenameValidation_runsBeforeRead() {
            when(fileSource.getOriginalFilename()).thenReturn("../evil.txt");

            assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket"));

            // InputStream should never be read if filename is invalid
            try {
                verify(fileSource, never()).getInputStream();
            } catch (IOException ignored) {
            }
        }
    }

    // ── File size validation ─────────────────────────────────────────

    @Nested
    class FileSizeValidation {

        @Test
        void fileTooLarge_throws() {
            when(fileSource.getSize()).thenReturn(100L);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> serviceLimited.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.FILE_TOO_LARGE, ex.getMessageKey());
            assertTrue(ex.getMessage().contains("100"));
            assertTrue(ex.getMessage().contains("10"));
        }

        @Test
        void fileExactlyAtLimit_allowed() throws IOException {
            setupSuccessfulUploadWithSize(10);

            FileMetadata result = serviceLimited.upload(fileSource, StorageType.LOCAL, "bucket");
            assertNotNull(result);
        }

        @Test
        void fileOneByteTooLarge_throws() {
            when(fileSource.getSize()).thenReturn(11L);
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> serviceLimited.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.FILE_TOO_LARGE, ex.getMessageKey());
        }

        @Test
        void unlimitedSize_allowsLargeFiles() throws IOException {
            byte[] content = "hello".getBytes();
            FileFormat format = new FileFormat("text/plain", "txt", "text");
            FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

            when(fileSource.getSize()).thenReturn(Long.MAX_VALUE);
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
            when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
            when(formatExtractor.extract(any())).thenReturn(format);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.upload(any())).thenReturn(location);
            when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket");
            assertNotNull(result);
        }

        @Test
        void sizeCheck_runsBeforeFilenameValidation() {
            when(fileSource.getSize()).thenReturn(100L);
            // filename is not set, but size check should fail first

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> serviceLimited.upload(fileSource, StorageType.LOCAL, "bucket"));
            assertEquals(FileStorageException.FILE_TOO_LARGE, ex.getMessageKey());
        }
    }

    // ── Upload flow ──────────────────────────────────────────────────

    @Nested
    class UploadFlow {

        @Test
        void fullFlow_uploadAndReturnMetadata() throws IOException {
            byte[] content = "hello".getBytes();
            FileFormat format = new FileFormat("text/plain", "txt", "text");
            FileLocation location = new FileLocation("my-bucket", "some-key", StorageType.LOCAL);

            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
            when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
            when(formatExtractor.extract(any())).thenReturn(format);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.upload(any())).thenReturn(location);
            when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "my-bucket");

            assertNotNull(result);
            assertEquals("test.txt", result.name());
            assertEquals(content.length, result.size());
            assertEquals("abc123", result.checksum());
            assertEquals(format, result.format());
            assertEquals(location, result.location());

            ArgumentCaptor<FileUploadCommand> cmdCaptor = ArgumentCaptor.forClass(FileUploadCommand.class);
            verify(fileStorage).upload(cmdCaptor.capture());
            FileUploadCommand cmd = cmdCaptor.getValue();
            assertEquals("test.txt", cmd.originalFilename());
            assertEquals("text/plain", cmd.mimeType());
            assertEquals("txt", cmd.extension());
            assertEquals("my-bucket", cmd.bucket());
            assertEquals(content.length, cmd.contentLength());
        }

        @Test
        void duplicateChecksum_returnsExistingWithoutUpload() throws IOException {
            byte[] content = "hello".getBytes();
            FileMetadata existing = new FileMetadata("existing-key", "test.txt", 5, "abc123",
                    new FileFormat("text/plain", "txt", "text"),
                    new FileLocation("bucket", "key", StorageType.LOCAL));

            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
            when(metadataRepository.findByChecksum("abc123")).thenReturn(existing);

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "my-bucket");

            assertEquals(existing, result);
            verify(formatExtractor, never()).extract(any());
            verify(storageResolver, never()).resolve(any());
            verify(metadataRepository, never()).save(any());
        }

        @Test
        void metadataSaved_afterStorage() throws IOException {
            setupSuccessfulUpload("test.txt");

            service.upload(fileSource, StorageType.LOCAL, "bucket");

            verify(metadataRepository).save(any());
        }
    }

    // ── Callback handling ────────────────────────────────────────────

    @Nested
    class CallbackHandling {

        @Test
        void callback_runsBeforeSave() throws Exception {
            setupSuccessfulUpload("test.txt");

            UploadCallback callback = mock(UploadCallback.class);
            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket", callback);

            assertNotNull(result);
            verify(callback).onUploaded(any());
            verify(metadataRepository).save(any());
            verify(fileStorage, never()).delete(any());
        }

        @Test
        void callback_receivesMetadataWithCorrectFields() throws Exception {
            byte[] content = "hello".getBytes();
            FileFormat format = new FileFormat("text/plain", "txt", "text");
            FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(fileSource.getOriginalFilename()).thenReturn("report.txt");
            when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
            when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
            when(formatExtractor.extract(any())).thenReturn(format);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.upload(any())).thenReturn(location);
            when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<FileMetadata> metaCaptor = ArgumentCaptor.forClass(FileMetadata.class);
            UploadCallback callback = mock(UploadCallback.class);
            service.upload(fileSource, StorageType.LOCAL, "bucket", callback);

            verify(callback).onUploaded(metaCaptor.capture());
            FileMetadata meta = metaCaptor.getValue();
            assertEquals("report.txt", meta.name());
            assertEquals(content.length, meta.size());
            assertEquals("abc123", meta.checksum());
        }

        @Test
        void callbackFailure_deletesFileAndThrows() throws Exception {
            setupSuccessfulUpload("test.txt");

            UploadCallback callback = mock(UploadCallback.class);
            doThrow(new RuntimeException("business error")).when(callback).onUploaded(any());

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket", callback));

            assertEquals(FileStorageException.CALLBACK_FAILED, ex.getMessageKey());
            verify(fileStorage).delete(any());
            verify(metadataRepository, never()).save(any());
        }

        @Test
        void callbackCheckedExceptionFailure_wrapsInFileStorageException() throws Exception {
            setupSuccessfulUpload("test.txt");

            UploadCallback callback = mock(UploadCallback.class);
            doThrow(new Exception("checked error")).when(callback).onUploaded(any());

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket", callback));

            assertEquals(FileStorageException.CALLBACK_FAILED, ex.getMessageKey());
            assertNotNull(ex.getCause());
            assertEquals("checked error", ex.getCause().getMessage());
            verify(fileStorage).delete(any());
        }

        @Test
        void callbackFailure_exceptionMessageContainsKey() throws Exception {
            setupSuccessfulUpload("test.txt");

            UploadCallback callback = mock(UploadCallback.class);
            doThrow(new RuntimeException("fail")).when(callback).onUploaded(any());

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.upload(fileSource, StorageType.LOCAL, "bucket", callback));

            assertTrue(ex.getMessage().contains("callback failed"),
                    "Message should indicate callback failure");
        }
    }

    // ── Virus scan integration ────────────────────────────────────────

    @Nested
    class VirusScanIntegration {

        VirusScanner virusScanner = mock(VirusScanner.class);

        FileUploadService serviceWithScanner = new FileUploadService(
                checksumCalculator, metadataRepository, formatExtractor,
                storageResolver, 0, virusScanner);

        @Test
        void infected_throwsAndDoesNotUpload() throws IOException {
            byte[] content = "hello".getBytes();
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.infected("EICAR"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket"));

            assertEquals(FileStorageException.VIRUS_DETECTED, ex.getMessageKey());
            assertTrue(ex.getMessage().contains("EICAR"));
            verify(storageResolver, never()).resolve(any());
            verify(metadataRepository, never()).save(any());
        }

        @Test
        void infected_doesNotComputeChecksum() throws IOException {
            byte[] content = "hello".getBytes();
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.infected("Trojan.Gen"));

            assertThrows(FileStorageException.class,
                    () -> serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket"));

            verify(checksumCalculator, never()).checksum(any(InputStream.class));
        }

        @Test
        void infected_withCallback_callbackNotInvoked() throws Exception {
            byte[] content = "hello".getBytes();
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.infected("Malware"));

            UploadCallback callback = mock(UploadCallback.class);

            assertThrows(FileStorageException.class,
                    () -> serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket", callback));

            verify(callback, never()).onUploaded(any());
        }

        @Test
        void clean_proceedsWithUpload() throws IOException {
            byte[] content = "hello".getBytes();
            FileFormat format = new FileFormat("text/plain", "txt", "text");
            FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.clean());
            when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
            when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
            when(formatExtractor.extract(any())).thenReturn(format);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.upload(any())).thenReturn(location);
            when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata result = serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket");

            assertNotNull(result);
            assertEquals("test.txt", result.name());
            verify(virusScanner).scan(any(InputStream.class));
            verify(metadataRepository).save(any());
        }

        @Test
        void clean_withDuplicate_returnsDuplicateWithoutStorage() throws IOException {
            byte[] content = "hello".getBytes();
            FileMetadata existing = new FileMetadata("existing-key", "test.txt", 5, "abc123",
                    new FileFormat("text/plain", "txt", "text"),
                    new FileLocation("bucket", "key", StorageType.LOCAL));

            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.clean());
            when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
            when(metadataRepository.findByChecksum("abc123")).thenReturn(existing);

            FileMetadata result = serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket");

            assertEquals(existing, result);
            verify(virusScanner).scan(any(InputStream.class));
            verify(storageResolver, never()).resolve(any());
        }

        @Test
        void error_throwsAndDoesNotUpload() throws IOException {
            byte[] content = "hello".getBytes();
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.error("Scan service unavailable"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket"));

            assertEquals(FileStorageException.VIRUS_SCAN_ERROR, ex.getMessageKey());
            assertTrue(ex.getMessage().contains("Scan service unavailable"));
            verify(storageResolver, never()).resolve(any());
            verify(metadataRepository, never()).save(any());
        }

        @Test
        void error_doesNotComputeChecksum() throws IOException {
            byte[] content = "hello".getBytes();
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.error("timeout"));

            assertThrows(FileStorageException.class,
                    () -> serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket"));

            verify(checksumCalculator, never()).checksum(any(InputStream.class));
        }

        @Test
        void noScanner_skipsScan() throws IOException {
            // service (without scanner) should work without calling any scanner
            setupSuccessfulUpload("test.txt");

            FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket");

            assertNotNull(result);
            // no virus scanner mock to verify — just ensure upload completes
        }

        @Test
        void scanRunsAfterFileRead() throws IOException {
            byte[] content = "hello".getBytes();
            when(fileSource.getOriginalFilename()).thenReturn("test.txt");
            when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(virusScanner.scan(any(InputStream.class))).thenReturn(ScanResult.infected("virus"));

            assertThrows(FileStorageException.class,
                    () -> serviceWithScanner.upload(fileSource, StorageType.LOCAL, "bucket"));

            // InputStream was read (to get bytes for scanning)
            verify(fileSource).getInputStream();
            verify(virusScanner).scan(any(InputStream.class));
        }

        @Test
        void constructorAcceptsNullScanner() {
            FileUploadService svc = new FileUploadService(
                    checksumCalculator, metadataRepository, formatExtractor,
                    storageResolver, 0, null);
            assertNotNull(svc);
        }
    }

    // ── Helper methods ───────────────────────────────────────────────

    private void setupSuccessfulUpload(String filename) throws IOException {
        byte[] content = "hello".getBytes();
        FileFormat format = new FileFormat("text/plain", "txt", "text");
        FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

        when(fileSource.getOriginalFilename()).thenReturn(filename);
        when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
        when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
        when(formatExtractor.extract(any())).thenReturn(format);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
        when(fileStorage.upload(any())).thenReturn(location);
        when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void setupSuccessfulUploadWithSize(long size) throws IOException {
        byte[] content = new byte[(int) size];
        FileFormat format = new FileFormat("text/plain", "txt", "text");
        FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

        when(fileSource.getSize()).thenReturn(size);
        when(fileSource.getOriginalFilename()).thenReturn("test.txt");
        when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(checksumCalculator.checksum(any(InputStream.class))).thenReturn("abc123");
        when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
        when(formatExtractor.extract(any())).thenReturn(format);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
        when(fileStorage.upload(any())).thenReturn(location);
        when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
}
