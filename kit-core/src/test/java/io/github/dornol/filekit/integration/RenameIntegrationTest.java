package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.metadata.FileRenameService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.memory.InMemoryFileStorage;
import io.github.dornol.filekit.test.InMemoryMetadataRepository;
import io.github.dornol.filekit.test.TestFileSource;
import io.github.dornol.filekit.upload.FileUploadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenameIntegrationTest {

    enum StorageType { MEMORY }

    private InMemoryMetadataRepository metadataRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;
    private FileRenameService renameService;

    @BeforeEach
    void setUp() {
        InMemoryFileStorage storage = new InMemoryFileStorage(StorageType.MEMORY);
        metadataRepository = new InMemoryMetadataRepository();
        FileStorageResolver resolver = new FileStorageResolver(List.of(storage));

        uploadService = FileUploadService.builder(new Sha256ChecksumCalculator(), metadataRepository,
                is -> new FileFormat("text/plain", "txt", "text"), resolver).build();
        downloadService = FileDownloadService.builder(metadataRepository, resolver).build();
        renameService = FileRenameService.builder(metadataRepository).build();
    }

    @Test
    void rename_preservesContentAndMetadata() throws IOException {
        byte[] content = "hello".getBytes();
        FileMetadata uploaded = uploadService.upload(
                new TestFileSource("original.txt", content), StorageType.MEMORY, "bucket");

        FileMetadata renamed = renameService.rename(uploaded.key(), "renamed.txt");

        assertEquals(uploaded.key(), renamed.key());
        assertEquals("renamed.txt", renamed.name());
        assertNotEquals(uploaded.name(), renamed.name());
        assertEquals(uploaded.checksum(), renamed.checksum());
        assertEquals(uploaded.size(), renamed.size());
        assertEquals(uploaded.format(), renamed.format());
        assertEquals(uploaded.location(), renamed.location());

        try (InputStream is = downloadService.download(renamed.key()).content()) {
            assertArrayEquals(content, is.readAllBytes());
        }
    }

    @Test
    void rename_metadataRepositoryUpdated() throws IOException {
        FileMetadata uploaded = uploadService.upload(
                new TestFileSource("before.txt", "data".getBytes()), StorageType.MEMORY, "bucket");

        renameService.rename(uploaded.key(), "after.txt");

        FileMetadata found = metadataRepository.findByKey(uploaded.key());
        assertEquals("after.txt", found.name());
    }

    @Test
    void rename_nonExistentKey_throws() {
        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> renameService.rename("non-existent-key", "new.txt"));
        assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
    }

    @Test
    void rename_multipleTimes() throws IOException {
        FileMetadata uploaded = uploadService.upload(
                new TestFileSource("v1.txt", "data".getBytes()), StorageType.MEMORY, "bucket");

        FileMetadata v2 = renameService.rename(uploaded.key(), "v2.txt");
        assertEquals("v2.txt", v2.name());

        FileMetadata v3 = renameService.rename(uploaded.key(), "v3.txt");
        assertEquals("v3.txt", v3.name());

        FileMetadata found = metadataRepository.findByKey(uploaded.key());
        assertEquals("v3.txt", found.name());
    }

    @Test
    void rename_doesNotAffectOtherFiles() throws IOException {
        FileMetadata file1 = uploadService.upload(
                new TestFileSource("file1.txt", "aaa".getBytes()), StorageType.MEMORY, "bucket");
        FileMetadata file2 = uploadService.upload(
                new TestFileSource("file2.txt", "bbb".getBytes()), StorageType.MEMORY, "bucket");

        renameService.rename(file1.key(), "renamed.txt");

        assertEquals("file2.txt", metadataRepository.findByKey(file2.key()).name());
    }
}
