package io.github.dornol.filekit.integration;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.metadata.FileRenameService;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
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
}
