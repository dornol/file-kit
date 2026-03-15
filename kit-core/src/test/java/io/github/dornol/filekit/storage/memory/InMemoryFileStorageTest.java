package io.github.dornol.filekit.storage.memory;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryFileStorageTest {

    enum StorageType { MEM }

    InMemoryFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryFileStorage(StorageType.MEM);
    }

    @Test
    void upload_storesAndReturnsLocation() {
        FileUploadCommand command = new FileUploadCommand(
                "key1", "photo.png", "hello".getBytes(),
                "image/png", "png", "uploads");

        FileLocation location = storage.upload(command);

        assertEquals("uploads", location.bucket());
        assertEquals("key1.png", location.objectKey());
        assertEquals(StorageType.MEM, location.storageType());
        assertEquals(1, storage.size());
    }

    @Test
    void load_returnsUploadedContent() throws IOException {
        byte[] content = "file-data".getBytes();
        FileUploadCommand command = new FileUploadCommand(
                "key1", "doc.txt", content, "text/plain", "txt", "bucket");
        storage.upload(command);

        FileMetadata metadata = new FileMetadata("key1", "doc.txt", content.length, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "key1.txt", StorageType.MEM));

        try (InputStream is = storage.load(metadata)) {
            assertArrayEquals(content, is.readAllBytes());
        }
    }

    @Test
    void load_throwsWhenNotFound() {
        FileMetadata metadata = new FileMetadata("missing", "x.txt", 0, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "missing.txt", StorageType.MEM));

        assertThrows(IllegalArgumentException.class, () -> storage.load(metadata));
    }

    @Test
    void upload_clonesBytesToPreventMutation() throws IOException {
        byte[] content = "original".getBytes();
        FileUploadCommand command = new FileUploadCommand(
                "key1", "f.txt", content, "text/plain", "txt", "b");
        storage.upload(command);

        content[0] = 'X'; // mutate original

        FileMetadata metadata = new FileMetadata("key1", "f.txt", 8, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("b", "key1.txt", StorageType.MEM));

        try (InputStream is = storage.load(metadata)) {
            assertArrayEquals("original".getBytes(), is.readAllBytes());
        }
    }

    @Test
    void clear_removesAllFiles() {
        storage.upload(new FileUploadCommand("k1", "a.txt", "a".getBytes(), "text/plain", "txt", "b"));
        storage.upload(new FileUploadCommand("k2", "b.txt", "b".getBytes(), "text/plain", "txt", "b"));
        assertEquals(2, storage.size());

        storage.clear();
        assertEquals(0, storage.size());
    }

    @Test
    void resolveUri_returnsMemoryScheme() {
        FileMetadata metadata = new FileMetadata("key1", "f.txt", 5, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "key1.txt", StorageType.MEM));

        String uri = storage.resolveUri(metadata);
        assertTrue(uri.startsWith("memory://"));
        assertTrue(uri.contains("bucket/key1.txt"));
    }

}
