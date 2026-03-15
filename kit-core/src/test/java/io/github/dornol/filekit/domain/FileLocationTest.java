package io.github.dornol.filekit.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileLocationTest {

    enum Storage { LOCAL, S3 }

    @Nested
    class Construction {

        @Test
        void validConstruction() {
            FileLocation loc = new FileLocation("my-bucket", "obj-key", Storage.LOCAL);

            assertEquals("my-bucket", loc.bucket());
            assertEquals("obj-key", loc.objectKey());
            assertEquals(Storage.LOCAL, loc.storageType());
        }

        @ParameterizedTest
        @ValueSource(strings = {"bucket", "my-bucket", "my.bucket", "my_bucket", "bucket123", "A.B-C_D"})
        void validBucketNames(String bucket) {
            assertDoesNotThrow(() -> new FileLocation(bucket, "key", Storage.LOCAL));
        }
    }

    @Nested
    class NullValidation {

        @Test
        void nullBucket_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileLocation(null, "key", Storage.LOCAL));
            assertEquals("bucket", ex.getMessage());
        }

        @Test
        void nullObjectKey_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileLocation("bucket", null, Storage.LOCAL));
            assertEquals("objectKey", ex.getMessage());
        }

        @Test
        void nullStorageType_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileLocation("bucket", "key", null));
            assertEquals("storageType", ex.getMessage());
        }
    }

    @Nested
    class BucketNameValidation {

        @ParameterizedTest
        @ValueSource(strings = {"bad bucket", "my/bucket", "my\\bucket", "bucket!", "bucket@name",
                "bucket#", "bucket$", "bucket%", "bucket^", "bucket&", "bucket*",
                "bucket(", "bucket)", "bucket+", "bucket=", "bucket{", "bucket}",
                "bucket[", "bucket]", "bucket|", "bucket:", "bucket;", "bucket'",
                "bucket\"", "bucket<", "bucket>", "bucket,", "bucket?", "bucket`",
                "bucket~"})
        void invalidBucketNames_throws(String bucket) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new FileLocation(bucket, "key", Storage.LOCAL));
            assertEquals("Invalid bucket name: " + bucket, ex.getMessage());
        }

        @Test
        void emptyBucket_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FileLocation("", "key", Storage.LOCAL));
        }

        @Test
        void bucketWithSpace_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FileLocation("my bucket", "key", Storage.LOCAL));
        }

        @Test
        void bucketWithNewline_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FileLocation("bucket\n", "key", Storage.LOCAL));
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equals_sameValues() {
            FileLocation a = new FileLocation("bucket", "key", Storage.LOCAL);
            FileLocation b = new FileLocation("bucket", "key", Storage.LOCAL);
            assertEquals(a, b);
        }

        @Test
        void notEquals_differentBucket() {
            FileLocation a = new FileLocation("bucket1", "key", Storage.LOCAL);
            FileLocation b = new FileLocation("bucket2", "key", Storage.LOCAL);
            assertNotEquals(a, b);
        }

        @Test
        void notEquals_differentStorageType() {
            FileLocation a = new FileLocation("bucket", "key", Storage.LOCAL);
            FileLocation b = new FileLocation("bucket", "key", Storage.S3);
            assertNotEquals(a, b);
        }

        @Test
        void hashCode_sameValues() {
            FileLocation a = new FileLocation("bucket", "key", Storage.LOCAL);
            FileLocation b = new FileLocation("bucket", "key", Storage.LOCAL);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
