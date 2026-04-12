package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for encryption-at-rest round-trip using a real AES encryptor.
 */
class EncryptionIntegrationTest {

    enum StorageType { MEMORY }

    private InMemoryFileStorage memoryStorage;
    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        memoryStorage = new InMemoryFileStorage(StorageType.MEMORY);
        metadataRepository = new InMemoryMetadataRepository();
        ChecksumCalculator checksumCalculator = new Sha256ChecksumCalculator();
        FileFormatExtractor formatExtractor = is -> new FileFormat("text/plain", "txt", "text");
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(memoryStorage));

        AesFileEncryptor encryptor = new AesFileEncryptor();

        uploadService = FileUploadService.builder(checksumCalculator, metadataRepository,
                formatExtractor, storageResolver).fileEncryptor(encryptor).build();
        downloadService = FileDownloadService.builder(metadataRepository, storageResolver)
                .fileEncryptor(encryptor).build();
    }

    @Nested
    class RoundTrip {

        @Test
        void uploadAndDownload_contentPreserved() throws IOException {
            byte[] content = "Hello, encrypted file-kit!".getBytes();

            FileMetadata uploaded = uploadService.upload(
                    new TestFileSource("secret.txt", content), StorageType.MEMORY, "vault");

            DownloadResult result = downloadService.download(uploaded.key());
            try (InputStream is = result.content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }

        @Test
        void emptyFile_roundTrip() throws IOException {
            byte[] content = new byte[0];

            FileMetadata uploaded = uploadService.upload(
                    new TestFileSource("empty.txt", content), StorageType.MEMORY, "vault");

            assertEquals(0, uploaded.size());

            DownloadResult result = downloadService.download(uploaded.key());
            try (InputStream is = result.content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }

        @Test
        void largeFile_roundTrip() throws IOException {
            byte[] content = new byte[1024 * 1024]; // 1 MB
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 251);
            }

            FileMetadata uploaded = uploadService.upload(
                    new TestFileSource("large.bin", content), StorageType.MEMORY, "vault");

            DownloadResult result = downloadService.download(uploaded.key());
            try (InputStream is = result.content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }
    }

    @Nested
    class StoredContent {

        @Test
        void storedContent_differsFromOriginal() throws IOException {
            byte[] content = "plaintext content that should be encrypted".getBytes();

            FileMetadata uploaded = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.MEMORY, "vault");

            // InMemoryFileStorage.load() returns raw stored bytes (no decryption layer)
            byte[] storedRaw;
            try (InputStream is = memoryStorage.load(uploaded)) {
                storedRaw = is.readAllBytes();
            }
            assertFalse(java.util.Arrays.equals(content, storedRaw),
                    "Stored content should differ from plaintext");
        }
    }

    @Nested
    class Deduplication {

        @Test
        void checksumOnPlaintext_dedupWorks() throws IOException {
            byte[] content = "dedup test content".getBytes();

            FileMetadata first = uploadService.upload(
                    new TestFileSource("first.txt", content), StorageType.MEMORY, "vault");
            FileMetadata second = uploadService.upload(
                    new TestFileSource("second.txt", content), StorageType.MEMORY, "vault");

            assertEquals(first.key(), second.key());
            assertEquals(first.checksum(), second.checksum());
        }
    }

    // ── Inner AES encryptor ──────────────────────────────────────────────

    static class AesFileEncryptor implements FileEncryptor {

        private final SecretKey secretKey;

        AesFileEncryptor() throws GeneralSecurityException {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            this.secretKey = keyGen.generateKey();
        }

        @Override
        public void encrypt(InputStream plainInput, OutputStream cipherOutput) throws IOException {
            try {
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                try (CipherOutputStream cos = new CipherOutputStream(cipherOutput, cipher)) {
                    plainInput.transferTo(cos);
                }
            } catch (GeneralSecurityException e) {
                throw new IOException("Encryption failed", e);
            }
        }

        @Override
        public void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException {
            try {
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                try (CipherInputStream cis = new CipherInputStream(cipherInput, cipher)) {
                    cis.transferTo(plainOutput);
                }
            } catch (GeneralSecurityException e) {
                throw new IOException("Decryption failed", e);
            }
        }
    }
}
