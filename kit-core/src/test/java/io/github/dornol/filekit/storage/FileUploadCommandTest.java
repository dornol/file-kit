package io.github.dornol.filekit.storage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileUploadCommandTest {

    private final byte[] content = "hello".getBytes();

    @Nested
    class Construction {

        @Test
        void validConstruction() {
            FileUploadCommand cmd = new FileUploadCommand(
                    "key", "file.txt", content, "text/plain", "txt", "bucket");

            assertEquals("key", cmd.key());
            assertEquals("file.txt", cmd.originalFilename());
            assertArrayEquals(content, cmd.content());
            assertEquals("text/plain", cmd.mimeType());
            assertEquals("txt", cmd.extension());
            assertEquals("bucket", cmd.bucket());
        }

        @Test
        void nullOriginalFilename_allowed() {
            FileUploadCommand cmd = new FileUploadCommand(
                    "key", null, content, "text/plain", "txt", "bucket");

            assertNull(cmd.originalFilename());
        }

        @Test
        void emptyContent_allowed() {
            assertDoesNotThrow(() -> new FileUploadCommand(
                    "key", "f.txt", new byte[0], "text/plain", "txt", "bucket"));
        }
    }

    @Nested
    class NullValidation {

        @Test
        void nullKey_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileUploadCommand(null, "f.txt", content, "text/plain", "txt", "bucket"));
            assertEquals("key", ex.getMessage());
        }

        @Test
        void nullContent_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileUploadCommand("key", "f.txt", null, "text/plain", "txt", "bucket"));
            assertEquals("content", ex.getMessage());
        }

        @Test
        void nullMimeType_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileUploadCommand("key", "f.txt", content, null, "txt", "bucket"));
            assertEquals("mimeType", ex.getMessage());
        }

        @Test
        void nullExtension_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileUploadCommand("key", "f.txt", content, "text/plain", null, "bucket"));
            assertEquals("extension", ex.getMessage());
        }

        @Test
        void nullBucket_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileUploadCommand("key", "f.txt", content, "text/plain", "txt", null));
            assertEquals("bucket", ex.getMessage());
        }
    }

    @Nested
    class BucketValidation {

        @ParameterizedTest
        @ValueSource(strings = {"bucket", "my-bucket", "my.bucket", "my_bucket", "Bucket123"})
        void validBucketNames(String bucket) {
            assertDoesNotThrow(() -> new FileUploadCommand(
                    "key", "f.txt", content, "text/plain", "txt", bucket));
        }

        @ParameterizedTest
        @ValueSource(strings = {"bad bucket", "my/bucket", "my\\bucket", "bucket!", "@bucket",
                "bucket#", "../escape", "bucket\n", ""})
        void invalidBucketNames_throws(String bucket) {
            assertThrows(IllegalArgumentException.class,
                    () -> new FileUploadCommand("key", "f.txt", content, "text/plain", "txt", bucket));
        }
    }
}
