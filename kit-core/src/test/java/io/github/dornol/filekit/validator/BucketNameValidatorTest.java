package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BucketNameValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"bucket", "my-bucket", "my.bucket", "my_bucket",
            "Bucket123", "A.B-C_D", "a", "123"})
    void validBucketNames(String bucket) {
        assertDoesNotThrow(() -> BucketNameValidator.validate(bucket));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad bucket", "my/bucket", "my\\bucket", "bucket!",
            "@bucket", "bucket#", "../escape", "bucket\n", "bucket\t",
            "bucket()", "bucket{}", "bucket+", "bucket="})
    void invalidBucketNames_throws(String bucket) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BucketNameValidator.validate(bucket));
        assertEquals("Invalid bucket name: " + bucket, ex.getMessage());
    }

    @Test
    void emptyBucket_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketNameValidator.validate(""));
    }

    @Test
    void patternConstant_isNotNull() {
        assertNotNull(BucketNameValidator.VALID_BUCKET_NAME);
    }
}
