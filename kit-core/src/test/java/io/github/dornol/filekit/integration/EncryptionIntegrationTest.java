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

import io.github.dornol.filekit.storage.FileStorageException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Nested
    class ChecksumVerificationWithEncryption {

        @Test
        void checksumVerification_passesWithEncryption() throws IOException, GeneralSecurityException {
            AesFileEncryptor encryptor = new AesFileEncryptor();
            Sha256ChecksumCalculator calc = new Sha256ChecksumCalculator();
            FileStorageResolver resolver = new FileStorageResolver(List.of(memoryStorage));

            FileUploadService upload = FileUploadService.builder(calc, metadataRepository,
                    is -> new FileFormat("text/plain", "txt", "text"), resolver)
                    .fileEncryptor(encryptor).build();

            FileDownloadService download = FileDownloadService.builder(metadataRepository, resolver)
                    .fileEncryptor(encryptor)
                    .checksumCalculator(calc)
                    .build();

            byte[] content = "checksum + encryption test".getBytes();
            FileMetadata uploaded = upload.upload(
                    new TestFileSource("verified.txt", content), StorageType.MEMORY, "vault");

            // checksum in metadata is based on plaintext
            assertEquals(calc.checksum(content), uploaded.checksum());

            // download decrypts first, then verifies checksum against plaintext
            DownloadResult result = download.download(uploaded.key());
            try (InputStream is = result.content()) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }

        @Test
        void checksumVerification_detectsCorruption() throws IOException, GeneralSecurityException {
            AesFileEncryptor encryptor = new AesFileEncryptor();
            Sha256ChecksumCalculator calc = new Sha256ChecksumCalculator();
            InMemoryFileStorage corruptibleStorage = new InMemoryFileStorage(StorageType.MEMORY);
            InMemoryMetadataRepository repo = new InMemoryMetadataRepository();
            FileStorageResolver resolver = new FileStorageResolver(List.of(corruptibleStorage));

            FileUploadService upload = FileUploadService.builder(calc, repo,
                    is -> new FileFormat("text/plain", "txt", "text"), resolver)
                    .fileEncryptor(encryptor).build();

            // Upload normally
            byte[] content = "will be corrupted".getBytes();
            FileMetadata uploaded = upload.upload(
                    new TestFileSource("file.txt", content), StorageType.MEMORY, "vault");

            // Corrupt the stored data: delete and re-save with different encrypted content
            repo.deleteByKey(uploaded.key());
            corruptibleStorage.delete(uploaded);

            // Re-upload different content but manually save with original checksum
            AesFileEncryptor encryptor2 = new AesFileEncryptor();
            FileUploadService upload2 = FileUploadService.builder(calc, repo,
                    is -> new FileFormat("text/plain", "txt", "text"), resolver)
                    .fileEncryptor(encryptor2).build();
            FileMetadata tampered = upload2.upload(
                    new TestFileSource("file.txt", "different content".getBytes()),
                    StorageType.MEMORY, "vault");

            // Overwrite metadata with original checksum but pointing to tampered storage
            FileMetadata faked = new FileMetadata(
                    tampered.key(), tampered.name(), tampered.size(),
                    uploaded.checksum(), // original checksum — mismatch!
                    tampered.format(), tampered.location());
            repo.deleteByKey(tampered.key());
            repo.save(faked);

            // Download with checksum verification — should detect mismatch
            FileDownloadService download = FileDownloadService.builder(repo, resolver)
                    .fileEncryptor(encryptor2)
                    .checksumCalculator(calc)
                    .build();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> download.download(faked.key()));
            assertEquals(FileStorageException.CHECKSUM_MISMATCH, ex.getMessageKey());
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
