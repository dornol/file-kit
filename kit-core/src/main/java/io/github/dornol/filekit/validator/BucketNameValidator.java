package io.github.dornol.filekit.validator;

import java.util.regex.Pattern;

/**
 * Shared bucket name validation logic used by {@code FileLocation}
 * and {@code FileUploadCommand}.
 */
public final class BucketNameValidator {

    /** Allowed bucket name pattern: alphanumeric, dot, hyphen, underscore. */
    public static final Pattern VALID_BUCKET_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private BucketNameValidator() {
    }

    /**
     * Validates that the bucket name matches the allowed pattern.
     *
     * @param bucket bucket name to validate
     * @throws IllegalArgumentException if the bucket name is invalid
     */
    public static void validate(String bucket) {
        if (!VALID_BUCKET_NAME.matcher(bucket).matches()) {
            throw new IllegalArgumentException("Invalid bucket name: " + bucket);
        }
    }
}
