package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.spring.storage.SpringFileStorage;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringDownloadServiceTest {

    enum StorageType { LOCAL }

    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);

    SpringDownloadService service;

    private final FileMetadata metadata = new FileMetadata(
            "file-key", "test.txt", 5, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL)
    );

    @BeforeEach
    void setUp() {
        service = new SpringDownloadService(metadataRepository, storageResolver);
    }

    // ── No encryption (NoOp) ────────────────────────────────────────

    @Nested
    class NoEncryption {

        @Test
        void delegatesToSpringFileStorage() {
            Resource expected = mock(Resource.class);
            SpringFileStorage springStorage = mock(SpringFileStorage.class);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(springStorage);
            when(springStorage.loadResource(metadata)).thenReturn(expected);

            Resource result = service.loadResource("file-key");

            assertThat(result).isSameAs(expected);
        }

        @Test
        void wrapsInputStreamForPlainFileStorage() {
            InputStream content = new ByteArrayInputStream("hello".getBytes());
            FileStorage plainStorage = mock(FileStorage.class);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(plainStorage);
            when(plainStorage.load(metadata)).thenReturn(content);

            Resource result = service.loadResource("file-key");

            assertThat(result).isInstanceOf(InputStreamResource.class);
        }

        @Test
        void throwsWhenFileNotFound() {
            when(metadataRepository.getByKey("missing")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "not found"));

            assertThatThrownBy(() -> service.loadResource("missing"))
                    .isInstanceOf(FileStorageException.class);
        }
    }

    // ── With encryption ─────────────────────────────────────────────

    @Nested
    class WithEncryption {

        private SpringDownloadService encryptedService;

        /** Simple XOR-based encryptor for testing: encrypt == decrypt. */
        private final FileEncryptor xorEncryptor = new FileEncryptor() {
            @Override
            public void encrypt(InputStream plainInput, OutputStream cipherOutput) throws IOException {
                xorTransfer(plainInput, cipherOutput);
            }

            @Override
            public void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException {
                xorTransfer(cipherInput, plainOutput);
            }

            private void xorTransfer(InputStream in, OutputStream out) throws IOException {
                int b;
                while ((b = in.read()) != -1) {
                    out.write(b ^ 0x42);
                }
            }
        };

        @BeforeEach
        void setUp() {
            encryptedService = new SpringDownloadService(metadataRepository, storageResolver, xorEncryptor);
        }

        @Test
        void decryptsFromPlainFileStorage() throws IOException {
            // Encrypt "hello" with XOR
            byte[] encrypted = xorBytes("hello".getBytes());
            FileStorage plainStorage = mock(FileStorage.class);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(plainStorage);
            when(plainStorage.load(metadata)).thenReturn(new ByteArrayInputStream(encrypted));

            Resource result = encryptedService.loadResource("file-key");

            assertThat(result).isInstanceOf(InputStreamResource.class);
            try (InputStream is = result.getInputStream()) {
                assertThat(is.readAllBytes()).isEqualTo("hello".getBytes());
            }
        }

        @Test
        void decryptsFromSpringFileStorage() throws IOException {
            byte[] encrypted = xorBytes("hello".getBytes());
            SpringFileStorage springStorage = mock(SpringFileStorage.class);
            Resource encryptedResource = new ByteArrayResource(encrypted);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(springStorage);
            when(springStorage.loadResource(metadata)).thenReturn(encryptedResource);

            Resource result = encryptedService.loadResource("file-key");

            assertThat(result).isInstanceOf(InputStreamResource.class);
            try (InputStream is = result.getInputStream()) {
                assertThat(is.readAllBytes()).isEqualTo("hello".getBytes());
            }
        }

        @Test
        void decryptedStream_closeCleansUpTempFile() throws IOException {
            byte[] encrypted = xorBytes("temp".getBytes());
            FileStorage plainStorage = mock(FileStorage.class);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(plainStorage);
            when(plainStorage.load(metadata)).thenReturn(new ByteArrayInputStream(encrypted));

            Resource result = encryptedService.loadResource("file-key");

            // Read and close — should clean up temp file without errors
            try (InputStream is = result.getInputStream()) {
                byte[] decrypted = is.readAllBytes();
                assertThat(decrypted).isEqualTo("temp".getBytes());
            }
        }

        @Test
        void emptyFile_decryptsCorrectly() throws IOException {
            byte[] encrypted = xorBytes(new byte[0]);
            FileStorage plainStorage = mock(FileStorage.class);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(plainStorage);
            when(plainStorage.load(metadata)).thenReturn(new ByteArrayInputStream(encrypted));

            Resource result = encryptedService.loadResource("file-key");

            try (InputStream is = result.getInputStream()) {
                assertThat(is.readAllBytes()).isEmpty();
            }
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

            SpringDownloadService failingService = new SpringDownloadService(
                    metadataRepository, storageResolver, failingEncryptor);

            FileStorage plainStorage = mock(FileStorage.class);
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(plainStorage);
            when(plainStorage.load(metadata)).thenReturn(new ByteArrayInputStream("data".getBytes()));

            assertThatThrownBy(() -> failingService.loadResource("file-key"))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("Failed to decrypt file content");
        }

        @Test
        void springStorage_resourceReadFails_throwsDownloadFailed() throws IOException {
            SpringFileStorage springStorage = mock(SpringFileStorage.class);
            Resource badResource = mock(Resource.class);

            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(springStorage);
            when(springStorage.loadResource(metadata)).thenReturn(badResource);
            when(badResource.getInputStream()).thenThrow(new IOException("stream broken"));

            assertThatThrownBy(() -> encryptedService.loadResource("file-key"))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("Failed to read resource stream");
        }

        private byte[] xorBytes(byte[] input) {
            byte[] result = new byte[input.length];
            for (int i = 0; i < input.length; i++) {
                result[i] = (byte) (input[i] ^ 0x42);
            }
            return result;
        }
    }

    // ── Constructor validation ──────────────────────────────────────

    @Nested
    class ConstructorValidation {

        @Test
        void twoArgConstructor_usesNoOpEncryptor() {
            // Should not throw
            SpringDownloadService svc = new SpringDownloadService(metadataRepository, storageResolver);
            assertThat(svc).isNotNull();
        }

        @Test
        void nullMetadataRepository_throws() {
            assertThatThrownBy(() -> new SpringDownloadService(null, storageResolver))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void nullStorageResolver_throws() {
            assertThatThrownBy(() -> new SpringDownloadService(metadataRepository, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void nullFileEncryptor_throws() {
            assertThatThrownBy(() -> new SpringDownloadService(metadataRepository, storageResolver, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
