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

import io.github.dornol.filekit.storage.FileStorageException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        FileUploadCommand command = FileUploadCommand.ofBytes(
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
        FileUploadCommand command = FileUploadCommand.ofBytes(
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

        FileUploadCommand command = FileUploadCommand.ofBytes(
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

        FileUploadCommand command = FileUploadCommand.ofBytes(
                "key2", "img.jpg", "img".getBytes(), "image/jpeg", "jpg", "media");

        FileLocation location = dated.upload(command);

        // objectKey should contain date pattern like 2026/03/15/key2.jpg
        assertTrue(location.objectKey().matches("\\d{4}/\\d{2}/\\d{2}/key2\\.jpg"));
        Path expected = tempDir.resolve("media").resolve(location.objectKey());
        assertTrue(Files.exists(expected));
    }

    @Test
    void delete_removesFile() {
        FileUploadCommand command = FileUploadCommand.ofBytes(
                "del-key", "f.txt", "data".getBytes(), "text/plain", "txt", "bucket");
        storage.upload(command);

        Path filePath = tempDir.resolve("bucket/del-key.txt");
        assertTrue(Files.exists(filePath));

        FileMetadata metadata = new FileMetadata("del-key", "f.txt", 4, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "del-key.txt", StorageType.LOCAL));
        storage.delete(metadata);

        assertFalse(Files.exists(filePath));
    }

    @Test
    void delete_nonExistentFile_doesNotThrow() {
        FileMetadata metadata = new FileMetadata("no-key", "f.txt", 0, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "no-key.txt", StorageType.LOCAL));
        storage.delete(metadata); // should not throw
    }

    @Test
    void load_nonExistentFile_throws() {
        FileMetadata metadata = new FileMetadata("missing", "f.txt", 0, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "missing.txt", StorageType.LOCAL));
        assertThrows(FileStorageException.class, () -> storage.load(metadata));
    }

    @Test
    void resolveUri_returnsFileUri() {
        FileMetadata metadata = new FileMetadata("key1", "f.txt", 0, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "key1.txt", StorageType.LOCAL));
        String uri = storage.resolveUri(metadata);
        assertTrue(uri.startsWith("file:"));
        assertTrue(uri.contains("bucket/key1.txt"));
    }

    @Test
    void getStorageType_returnsConfiguredType() {
        assertEquals(StorageType.LOCAL, storage.getStorageType());
    }

    @Test
    void upload_pathTraversalInBucket_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                FileUploadCommand.ofBytes("key", "f.txt", "data".getBytes(),
                        "text/plain", "txt", "../../etc"));
    }

    @Test
    void upload_pathTraversalInObjectKey_rejected() {
        // key that escapes baseDir: baseDir/bucket/../../escape.txt -> baseDir/../escape.txt
        FileUploadCommand command = FileUploadCommand.ofBytes(
                "../../escape", "f.txt", "data".getBytes(),
                "text/plain", "txt", "bucket");
        assertThrows(FileStorageException.class, () -> storage.upload(command));
    }

    @Test
    void upload_errorMessageDoesNotExposeInternalPath() {
        FileUploadCommand command = FileUploadCommand.ofBytes(
                "../../../escape", "f.txt", "data".getBytes(),
                "text/plain", "txt", "bucket");
        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> storage.upload(command));
        assertFalse(ex.getMessage().contains(tempDir.toString()),
                "Error message should not contain internal path");
    }

    @Test
    void upload_emptyFile_succeeds() throws IOException {
        FileUploadCommand command = FileUploadCommand.ofBytes(
                "empty-key", "empty.txt", new byte[0], "text/plain", "txt", "bucket");

        FileLocation location = storage.upload(command);

        Path filePath = tempDir.resolve("bucket").resolve(location.objectKey());
        assertTrue(Files.exists(filePath));
        assertEquals(0, Files.size(filePath));
    }

    @Test
    void upload_largeFile_succeeds() throws IOException {
        byte[] content = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        FileUploadCommand command = FileUploadCommand.ofBytes(
                "large-key", "large.bin", content, "application/octet-stream", "bin", "bucket");

        FileLocation location = storage.upload(command);

        Path filePath = tempDir.resolve("bucket").resolve(location.objectKey());
        assertArrayEquals(content, Files.readAllBytes(filePath));
    }

    @Test
    void upload_thenLoad_roundTrip() throws IOException {
        byte[] content = "round trip".getBytes();
        FileUploadCommand command = FileUploadCommand.ofBytes(
                "rt-key", "rt.txt", content, "text/plain", "txt", "bucket");
        FileLocation location = storage.upload(command);

        FileMetadata metadata = new FileMetadata("rt-key", "rt.txt", content.length, "chk",
                new FileFormat("text/plain", "txt", "text"), location);

        try (InputStream is = storage.load(metadata)) {
            assertArrayEquals(content, is.readAllBytes());
        }
    }

    @Test
    void load_fileExistsButCannotResolveSymlink_throws() throws IOException {
        // This test verifies path validation on non-existent files
        FileMetadata metadata = new FileMetadata("key", "f.txt", 0, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("nonexistent-bucket", "key.txt", StorageType.LOCAL));

        // File does not exist, but path is valid — should throw because file is missing
        assertThrows(FileStorageException.class, () -> storage.load(metadata));
    }

    @Test
    void load_symlinkOutsideBaseDir_rejected() throws IOException {
        // Create a file outside baseDir
        Path outsideFile = Files.createTempFile("outside", ".txt");
        Files.writeString(outsideFile, "secret");

        // Create a symlink inside baseDir pointing outside
        Path bucketDir = tempDir.resolve("bucket");
        Files.createDirectories(bucketDir);
        Path symlink = bucketDir.resolve("evil.txt");
        Files.createSymbolicLink(symlink, outsideFile);

        FileMetadata metadata = new FileMetadata("evil", "evil.txt", 6, "chk",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "evil.txt", StorageType.LOCAL));

        assertThrows(FileStorageException.class, () -> storage.load(metadata));

        // Cleanup
        Files.deleteIfExists(symlink);
        Files.deleteIfExists(outsideFile);
    }

}
