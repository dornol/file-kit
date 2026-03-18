package io.github.dornol.filekit.download;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileDownloadServiceTest {

    enum StorageType { LOCAL }

    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);
    FileStorage fileStorage = mock(FileStorage.class);

    FileDownloadService service;

    private final FileMetadata metadata = new FileMetadata(
            "file-key", "test.txt", 5, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL)
    );

    @BeforeEach
    void setUp() {
        service = new FileDownloadService(metadataRepository, storageResolver);
    }

    // ── Download ─────────────────────────────────────────────────────

    @Nested
    class Download {

        @Test
        void returnsResultWithStream() {
            InputStream content = new ByteArrayInputStream("hello".getBytes());
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.load(metadata)).thenReturn(content);

            DownloadResult result = service.download("file-key");

            assertNotNull(result);
            assertEquals(metadata, result.metadata());
            assertEquals(content, result.content());
        }

        @Test
        void throwsWhenFileNotFound() {
            when(metadataRepository.getByKey("missing")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found: missing"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.download("missing"));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }

        @Test
        void nullFileKey_throws() {
            assertThrows(NullPointerException.class, () -> service.download(null));
        }
    }

    // ── Resolve URI ──────────────────────────────────────────────────

    @Nested
    class ResolveUri {

        @Test
        void returnsUri() {
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.resolveUri(metadata)).thenReturn("https://example.com/file");

            String uri = service.resolveUri("file-key");
            assertEquals("https://example.com/file", uri);
        }

        @Test
        void throwsWhenFileNotFound() {
            when(metadataRepository.getByKey("missing")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found: missing"));

            assertThrows(FileStorageException.class, () -> service.resolveUri("missing"));
        }

        @Test
        void nullFileKey_throws() {
            assertThrows(NullPointerException.class, () -> service.resolveUri(null));
        }
    }

    // ── Constructor validation ───────────────────────────────────────

    @Nested
    class ConstructorValidation {

        @Test
        void nullMetadataRepository_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileDownloadService(null, storageResolver));
        }

        @Test
        void nullStorageResolver_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileDownloadService(metadataRepository, null));
        }

        @Test
        void nullFileEncryptor_throws() {
            assertThrows(NullPointerException.class,
                    () -> new FileDownloadService(metadataRepository, storageResolver, null));
        }

        @Test
        void threeArgConstructor_valid() {
            FileDownloadService svc = new FileDownloadService(
                    metadataRepository, storageResolver, new NoOpFileEncryptor());
            assertNotNull(svc);
        }
    }

    // ── Decryption integration ──────────────────────────────────────

    @Nested
    class DecryptionIntegration {

        @Test
        void noOpEncryptor_returnsRawStream() {
            InputStream content = new ByteArrayInputStream("hello".getBytes());
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.load(metadata)).thenReturn(content);

            DownloadResult result = service.download("file-key");

            // With NoOp, the original stream is returned directly
            assertEquals(content, result.content());
        }

        @Test
        void customEncryptor_decryptsContent() throws IOException {
            // Simple XOR-based encryptor for testing
            FileEncryptor xorEncryptor = new FileEncryptor() {
                @Override
                public void encrypt(InputStream plainInput, OutputStream cipherOutput) throws IOException {
                    int b;
                    while ((b = plainInput.read()) != -1) {
                        cipherOutput.write(b ^ 0x42);
                    }
                }
                @Override
                public void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException {
                    int b;
                    while ((b = cipherInput.read()) != -1) {
                        plainOutput.write(b ^ 0x42);
                    }
                }
            };

            FileDownloadService encryptedService = new FileDownloadService(
                    metadataRepository, storageResolver, xorEncryptor);

            // Simulate encrypted storage content (XOR'd)
            byte[] plain = "hello".getBytes();
            byte[] encrypted = new byte[plain.length];
            for (int i = 0; i < plain.length; i++) encrypted[i] = (byte) (plain[i] ^ 0x42);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.load(metadata)).thenReturn(new ByteArrayInputStream(encrypted));

            DownloadResult result = encryptedService.download("file-key");

            byte[] decrypted = result.content().readAllBytes();
            assertArrayEquals(plain, decrypted);
        }

        @Test
        void decryptionFailure_throwsDecryptionFailed() {
            FileEncryptor failingEncryptor = new FileEncryptor() {
                @Override
                public void encrypt(InputStream plainInput, OutputStream cipherOutput) {}
                @Override
                public void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException {
                    throw new IOException("decryption error");
                }
            };

            FileDownloadService failingService = new FileDownloadService(
                    metadataRepository, storageResolver, failingEncryptor);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.load(metadata)).thenReturn(new ByteArrayInputStream("data".getBytes()));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> failingService.download("file-key"));
            assertEquals(FileStorageException.DECRYPTION_FAILED, ex.getMessageKey());
        }
    }

    // ── Pre-signed URL ───────────────────────────────────────────────

    @Nested
    class PresignedUrl {

        @Test
        void delegatesToStorage() {
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.generatePresignedUrl(metadata, Duration.ofHours(1)))
                    .thenReturn("https://example.com/presigned");

            String url = service.generatePresignedUrl("file-key", Duration.ofHours(1));

            assertEquals("https://example.com/presigned", url);
            verify(fileStorage).generatePresignedUrl(metadata, Duration.ofHours(1));
        }

        @Test
        void throwsWhenFileNotFound() {
            when(metadataRepository.getByKey("missing")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "File not found"));

            assertThrows(FileStorageException.class,
                    () -> service.generatePresignedUrl("missing", Duration.ofHours(1)));
        }

        @Test
        void throwsWhenStorageDoesNotSupport() {
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            when(fileStorage.generatePresignedUrl(metadata, Duration.ofHours(1)))
                    .thenThrow(new UnsupportedOperationException("Not supported"));

            assertThrows(UnsupportedOperationException.class,
                    () -> service.generatePresignedUrl("file-key", Duration.ofHours(1)));
        }

        @Test
        void nullFileKey_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.generatePresignedUrl(null, Duration.ofHours(1)));
        }

        @Test
        void nullExpiration_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.generatePresignedUrl("file-key", null));
        }

        @Test
        void shortExpiration() {
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            Duration shortDuration = Duration.ofSeconds(30);
            when(fileStorage.generatePresignedUrl(metadata, shortDuration))
                    .thenReturn("https://example.com/short");

            String url = service.generatePresignedUrl("file-key", shortDuration);

            assertEquals("https://example.com/short", url);
        }

        @Test
        void longExpiration() {
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
            Duration longDuration = Duration.ofDays(7);
            when(fileStorage.generatePresignedUrl(metadata, longDuration))
                    .thenReturn("https://example.com/long");

            String url = service.generatePresignedUrl("file-key", longDuration);

            assertEquals("https://example.com/long", url);
        }
    }
}
