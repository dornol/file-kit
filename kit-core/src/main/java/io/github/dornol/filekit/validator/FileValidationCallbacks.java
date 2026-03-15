package io.github.dornol.filekit.validator;

/**
 * Callback interface that defines individual file validation checks.
 *
 * <p>Implemented by concrete validators (e.g. {@link FileSourceValidator})
 * and invoked by {@link BaseFileValidationSupport} during the validation flow.</p>
 *
 * @param <T> the type of value being validated
 */
public interface FileValidationCallbacks<T> {

    /** Returns {@code true} if validation should be skipped (e.g. empty array). */
    boolean isValidationNotRequired(T value);

    /** Returns {@code true} if the detected media type is in the allowed set. */
    boolean isValidMediaType(T value);

    /** Returns {@code true} if the file is empty (zero bytes). */
    boolean isFileEmpty(T value);

    /** Returns {@code true} if the file exceeds the configured max size. */
    boolean isFileSizeExceeded(T value);

    /** Returns {@code true} if the filename is safe (no path traversal, reasonable length). */
    boolean isValidFilename(T value);

    /** Returns {@code true} if the file extension matches the detected content type. */
    boolean isValidExtension(T value);

}
