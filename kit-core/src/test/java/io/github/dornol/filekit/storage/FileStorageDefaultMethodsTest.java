package io.github.dornol.filekit.storage;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link FileStorage} default methods: generatePresignedUrl and loadRange.
 */
class FileStorageDefaultMethodsTest {

    enum StorageType { TEST }

    private final FileMetadata metadata = new FileMetadata(
            "key", "file.txt", 10, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "key.txt", StorageType.TEST)
    );

    @Nested
    class GeneratePresignedUrl {

        @Test
        void defaultImplementation_throwsUnsupportedOperationException() {
            FileStorage storage = stubStorage(new byte[0]);

            assertThrows(UnsupportedOperationException.class,
                    () -> storage.generatePresignedUrl(metadata, Duration.ofHours(1)));
        }
    }

    @Nested
    class LoadRange {

        @Test
        void readsCorrectByteRange() throws IOException {
            byte[] content = "0123456789".getBytes();
            FileStorage storage = stubStorage(content);

            // Range: bytes 2-5 → "2345"
            try (InputStream is = storage.loadRange(metadata, 2, 5)) {
                assertArrayEquals("2345".getBytes(), is.readAllBytes());
            }
        }

        @Test
        void readsFirstByte() throws IOException {
            byte[] content = "ABCDEF".getBytes();
            FileStorage storage = stubStorage(content);

            try (InputStream is = storage.loadRange(metadata, 0, 0)) {
                byte[] result = is.readAllBytes();
                assertEquals(1, result.length);
                assertEquals('A', result[0]);
            }
        }

        @Test
        void readsLastBytes() throws IOException {
            byte[] content = "ABCDEF".getBytes();
            FileStorage storage = stubStorage(content);

            try (InputStream is = storage.loadRange(metadata, 4, 5)) {
                assertArrayEquals("EF".getBytes(), is.readAllBytes());
            }
        }

        @Test
        void readsEntireContent() throws IOException {
            byte[] content = "ABCDEF".getBytes();
            FileStorage storage = stubStorage(content);

            try (InputStream is = storage.loadRange(metadata, 0, 5)) {
                assertArrayEquals(content, is.readAllBytes());
            }
        }

        @Test
        void skipFails_throwsFileStorageException() {
            // Storage that returns a stream that fails on skipNBytes
            FileStorage storage = new FileStorage() {
                @Override public Enum<?> getStorageType() { return StorageType.TEST; }
                @Override public FileLocation upload(FileUploadCommand command) { throw new UnsupportedOperationException(); }
                @Override public InputStream load(FileMetadata metadata) {
                    return new InputStream() {
                        @Override public int read() throws IOException { throw new IOException("skip failed"); }
                        @Override public long skip(long n) throws IOException { throw new IOException("skip failed"); }
                    };
                }
                @Override public void delete(FileMetadata metadata) {}
                @Override public String resolveUri(FileMetadata metadata) { return "test://"; }
            };

            assertThrows(FileStorageException.class,
                    () -> storage.loadRange(metadata, 10, 20));
        }
    }

    private static FileStorage stubStorage(byte[] content) {
        return new FileStorage() {
            @Override public Enum<?> getStorageType() { return StorageType.TEST; }
            @Override public FileLocation upload(FileUploadCommand command) { throw new UnsupportedOperationException(); }
            @Override public InputStream load(FileMetadata metadata) { return new ByteArrayInputStream(content); }
            @Override public void delete(FileMetadata metadata) {}
            @Override public String resolveUri(FileMetadata metadata) { return "test://"; }
        };
    }
}
