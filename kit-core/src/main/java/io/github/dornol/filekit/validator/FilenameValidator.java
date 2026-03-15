package io.github.dornol.filekit.validator;

import org.jspecify.annotations.Nullable;

/**
 * Shared filename validation logic used by both annotation-based validators
 * and the upload service.
 */
public final class FilenameValidator {

    /** Maximum allowed filename length. */
    public static final int MAX_FILENAME_LENGTH = 200;

    private FilenameValidator() {
    }

    /**
     * Returns {@code true} if the filename is safe (not null, not blank,
     * within length limit, no path traversal characters).
     *
     * @param filename the filename to validate
     * @return {@code true} if safe
     */
    public static boolean isSafe(@Nullable String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            return false;
        }
        return !containsTraversalCharacters(filename);
    }

    /**
     * Returns {@code true} if the filename contains path traversal characters
     * ({@code ..}, {@code /}, or {@code \}).
     *
     * @param filename the filename to check (must not be null)
     * @return {@code true} if dangerous characters are present
     */
    public static boolean containsTraversalCharacters(String filename) {
        return filename.contains("..") || filename.contains("/") || filename.contains("\\");
    }
}
