package io.github.dornol.filekit.validator;

import java.util.Objects;

/** Validates storage object keys while allowing safe hierarchical keys. */
public final class StorageKeyValidator {

    private StorageKeyValidator() {
    }

    /**
     * Validates a relative object key such as {@code 2026/08/file.txt}.
     */
    public static void validate(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey");
        if (objectKey.isBlank()
                || objectKey.startsWith("/")
                || objectKey.endsWith("/")
                || objectKey.contains("\\")
                || objectKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid object key: " + objectKey);
        }
        for (String segment : objectKey.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid object key: " + objectKey);
            }
        }
    }
}
