package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.quota.QuotaChecker;
import io.github.dornol.filekit.spi.QuotaPolicy;
import io.github.dornol.filekit.spi.QuotaUsageProvider;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotaIntegrationTest {

    enum StorageType { MEMORY }

    private InMemoryFileStorage memoryStorage;
    private InMemoryMetadataRepository metadataRepository;
    private AtomicLong usedBytes;
    private FileUploadService uploadService;
    private FileTransferService transferService;

    @BeforeEach
    void setUp() {
        memoryStorage = new InMemoryFileStorage(StorageType.MEMORY);
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver storageResolver = new FileStorageResolver(List.of(memoryStorage));
        usedBytes = new AtomicLong(0);

        QuotaPolicy policy = (storageType, bucket) -> 100L;
        QuotaUsageProvider usageProvider = (storageType, bucket) -> usedBytes.get();
        QuotaChecker quotaChecker = new QuotaChecker(policy, usageProvider);
        FileEventPublisher eventPublisher = new FileEventPublisher(List.of());

        uploadService = new FileUploadService(
                new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"),
                storageResolver, 0, null, new io.github.dornol.filekit.spi.NoOpFileEncryptor(),
                quotaChecker, eventPublisher);

        transferService = new FileTransferService(metadataRepository, storageResolver,
                quotaChecker, eventPublisher);
    }

    @Nested
    class Upload {

        @Test
        void withinQuota_succeeds() throws IOException {
            FileMetadata meta = uploadService.upload(
                    new TestFileSource("file.txt", "small".getBytes()), StorageType.MEMORY, "bucket");
            assertNotNull(meta);
        }

        @Test
        void exceedsQuota_rejected() {
            usedBytes.set(90);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> uploadService.upload(
                            new TestFileSource("file.txt", "this exceeds the quota".getBytes()),
                            StorageType.MEMORY, "bucket"));
            assertEquals(FileStorageException.QUOTA_EXCEEDED, ex.getMessageKey());
        }

        @Test
        void dedup_doesNotConsumeQuota() throws IOException {
            byte[] content = "dedup content".getBytes();
            uploadService.upload(new TestFileSource("a.txt", content), StorageType.MEMORY, "bucket");

            // Set usage to near limit
            usedBytes.set(95);

            // Same content should return existing (dedup hit), no quota check
            FileMetadata deduped = uploadService.upload(
                    new TestFileSource("b.txt", content), StorageType.MEMORY, "bucket");
            assertNotNull(deduped);
        }
    }

    @Nested
    class CopyQuota {

        @Test
        void copy_exceedsQuota_rejected() throws IOException {
            byte[] content = "copy me".getBytes();
            FileMetadata source = uploadService.upload(
                    new TestFileSource("file.txt", content), StorageType.MEMORY, "bucket-a");

            usedBytes.set(95);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> transferService.copy(source.key(), StorageType.MEMORY, "bucket-b"));
            assertEquals(FileStorageException.QUOTA_EXCEEDED, ex.getMessageKey());
        }
    }
}
