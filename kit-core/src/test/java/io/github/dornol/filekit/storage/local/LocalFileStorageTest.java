package io.github.dornol.filekit.storage.local;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageTest {

    enum StorageType { LOCAL }

    @TempDir
    Path tempDir;

    LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(tempDir, StorageType.LOCAL);
    }

    @Test
    void upload_writesFileAndReturnsLocation() {
        FileUploadCommand command = new FileUploadCommand(
                "test-key", "photo.png", "hello".getBytes(),
                "image/png", "png", "uploads");

        FileLocation location = storage.upload(command);

        assertEquals("uploads", location.bucket());
        assertEquals("test-key.png", location.objectKey());
        assertEquals(StorageType.LOCAL, location.storageType());
        assertTrue(Files.exists(tempDir.resolve("uploads/test-key.png")));
    }

    @Test
    void load_readsUploadedFile() throws IOException {
        byte[] content = "file-content".getBytes();
        FileUploadCommand command = new FileUploadCommand(
                "key1", "doc.txt", content, "text/plain", "txt", "bucket");
        storage.upload(command);

        FileMetadata metadata = new FileMetadata("key1", "doc.txt", content.length, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "key1.txt", StorageType.LOCAL));

        try (InputStream is = storage.load(metadata)) {
            assertArrayEquals(content, is.readAllBytes());
        }
    }

    @Test
    void upload_withHashPrefixedStrategy_createsSubdirectories() {
        LocalFileStorage hashed = new LocalFileStorage(tempDir, StorageType.LOCAL,
                ObjectKeyStrategy.hashPrefixed(2));

        FileUploadCommand command = new FileUploadCommand(
                "abcd1234-5678-9abc-def0-1234567890ab", "file.pdf",
                "data".getBytes(), "application/pdf", "pdf", "docs");

        FileLocation location = hashed.upload(command);

        assertTrue(location.objectKey().contains("/"));
        Path expected = tempDir.resolve("docs").resolve(location.objectKey());
        assertTrue(Files.exists(expected));
    }

    @Test
    void upload_withDateBasedStrategy_createsDateDirectories() {
        LocalFileStorage dated = new LocalFileStorage(tempDir, StorageType.LOCAL,
                ObjectKeyStrategy.dateBased());

        FileUploadCommand command = new FileUploadCommand(
                "key2", "img.jpg", "img".getBytes(), "image/jpeg", "jpg", "media");

        FileLocation location = dated.upload(command);

        // objectKey should contain date pattern like 2026/03/15/key2.jpg
        assertTrue(location.objectKey().matches("\\d{4}/\\d{2}/\\d{2}/key2\\.jpg"));
        Path expected = tempDir.resolve("media").resolve(location.objectKey());
        assertTrue(Files.exists(expected));
    }

    @Test
    void getStorageType_returnsConfiguredType() {
        assertEquals(StorageType.LOCAL, storage.getStorageType());
    }

}
