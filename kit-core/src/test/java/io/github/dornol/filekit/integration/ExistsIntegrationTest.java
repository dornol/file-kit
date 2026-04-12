package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExistsIntegrationTest {

    enum StorageType { MEMORY }

    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDeleteService deleteService;

    @BeforeEach
    void setUp() {
        InMemoryFileStorage storage = new InMemoryFileStorage(StorageType.MEMORY);
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver resolver = new FileStorageResolver(List.of(storage));

        uploadService = FileUploadService.builder(new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"), resolver).build();
        deleteService = FileDeleteService.builder(metadataRepository, resolver).build();
    }

    @Test
    void existsByKey_afterUpload_returnsTrue() throws IOException {
        FileMetadata meta = uploadService.upload(
                new TestFileSource("file.txt", "data".getBytes()), StorageType.MEMORY, "bucket");

        assertTrue(metadataRepository.existsByKey(meta.key()));
    }

    @Test
    void existsByKey_nonExistent_returnsFalse() {
        assertFalse(metadataRepository.existsByKey("non-existent-key"));
    }

    @Test
    void existsByKey_emptyString_returnsFalse() {
        assertFalse(metadataRepository.existsByKey(""));
    }

    @Test
    void existsByKey_afterDelete_returnsFalse() throws IOException {
        FileMetadata meta = uploadService.upload(
                new TestFileSource("file.txt", "data".getBytes()), StorageType.MEMORY, "bucket");

        assertTrue(metadataRepository.existsByKey(meta.key()));

        deleteService.delete(meta.key());

        assertFalse(metadataRepository.existsByKey(meta.key()));
    }

    @Test
    void existsByKey_multipleFiles_eachExists() throws IOException {
        FileMetadata m1 = uploadService.upload(
                new TestFileSource("a.txt", "aaa".getBytes()), StorageType.MEMORY, "bucket");
        FileMetadata m2 = uploadService.upload(
                new TestFileSource("b.txt", "bbb".getBytes()), StorageType.MEMORY, "bucket");

        assertTrue(metadataRepository.existsByKey(m1.key()));
        assertTrue(metadataRepository.existsByKey(m2.key()));
    }

    @Test
    void existsByKey_deleteOne_otherStillExists() throws IOException {
        FileMetadata m1 = uploadService.upload(
                new TestFileSource("a.txt", "aaa".getBytes()), StorageType.MEMORY, "bucket");
        FileMetadata m2 = uploadService.upload(
                new TestFileSource("b.txt", "bbb".getBytes()), StorageType.MEMORY, "bucket");

        deleteService.delete(m1.key());

        assertFalse(metadataRepository.existsByKey(m1.key()));
        assertTrue(metadataRepository.existsByKey(m2.key()));
    }
}
