package io.github.dornol.filekit.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadResultTest {

    private static FileMetadata sampleMetadata(String key) {
        return new FileMetadata(
                key, "file.txt", 100, "abc123",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "obj-key", TestStorageType.MEMORY)
        );
    }

    enum TestStorageType { MEMORY }

    @Nested
    class RecordFields {

        @Test
        void accessors() {
            FileMetadata metadata = sampleMetadata("key-1");
            InputStream content = new ByteArrayInputStream("data".getBytes());

            DownloadResult result = new DownloadResult(metadata, content);

            assertSame(metadata, result.metadata());
            assertSame(content, result.content());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameReference() {
            FileMetadata metadata = sampleMetadata("key-1");
            InputStream content = new ByteArrayInputStream("data".getBytes());

            DownloadResult a = new DownloadResult(metadata, content);
            DownloadResult b = new DownloadResult(metadata, content);

            // Same object references → equal
            assertEquals(a, b);
        }

        @Test
        void inequality_differentStreams() {
            FileMetadata metadata = sampleMetadata("key-1");

            DownloadResult a = new DownloadResult(metadata, new ByteArrayInputStream("data".getBytes()));
            DownloadResult b = new DownloadResult(metadata, new ByteArrayInputStream("data".getBytes()));

            // Different InputStream instances → not equal (reference equality)
            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentMetadata() {
            InputStream content = new ByteArrayInputStream("data".getBytes());

            DownloadResult a = new DownloadResult(sampleMetadata("key-1"), content);
            DownloadResult b = new DownloadResult(sampleMetadata("key-2"), content);

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsClassName() {
            DownloadResult result = new DownloadResult(
                    sampleMetadata("key-1"),
                    new ByteArrayInputStream("data".getBytes()));

            String str = result.toString();
            assertNotNull(str);
            assertTrue(str.startsWith("DownloadResult["));
        }
    }

    @Nested
    class Validation {

        @Test
        void nullMetadata_throws() {
            assertThrows(NullPointerException.class,
                    () -> new DownloadResult(null, new ByteArrayInputStream(new byte[0])));
        }

        @Test
        void nullContent_throws() {
            assertThrows(NullPointerException.class,
                    () -> new DownloadResult(sampleMetadata("key-1"), null));
        }
    }
}
