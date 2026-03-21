package io.github.dornol.filekit.validator;

/**
 * Constants for validation message keys used by file validators.
 *
 * <p>These keys are used with Jakarta Validation's message interpolation.
 * Applications can map them to localized messages in
 * {@code ValidationMessages.properties}.</p>
 *
 * @see FileValidationHelper
 * @see BaseFileValidationSupport
 */
public final class ValidationMessageKeys {

    private ValidationMessageKeys() {}

    /** File is empty (zero bytes). */
    public static final String FILE_EMPTY = "file-kit.validation.file-empty";

    /** File size exceeds the configured maximum. */
    public static final String FILE_TOO_LARGE = "file-kit.validation.file-too-large";

    /** Filename is invalid (null, blank, too long, or contains path traversal). */
    public static final String INVALID_FILENAME = "file-kit.validation.invalid-filename";

    /** Detected media type is not in the allowed set. */
    public static final String UNSUPPORTED_MEDIA_TYPE = "file-kit.validation.unsupported-media-type";

    /** File extension does not match the detected content type. */
    public static final String INVALID_EXTENSION = "file-kit.validation.invalid-extension";

    /** File is not a readable image (ImageIO cannot decode it). */
    public static final String IMAGE_NOT_READABLE = "file-kit.validation.image-not-readable";

    /** Image width is below the configured minimum. */
    public static final String IMAGE_WIDTH_TOO_SMALL = "file-kit.validation.image-width-too-small";

    /** Image width exceeds the configured maximum. */
    public static final String IMAGE_WIDTH_TOO_LARGE = "file-kit.validation.image-width-too-large";

    /** Image height is below the configured minimum. */
    public static final String IMAGE_HEIGHT_TOO_SMALL = "file-kit.validation.image-height-too-small";

    /** Image height exceeds the configured maximum. */
    public static final String IMAGE_HEIGHT_TOO_LARGE = "file-kit.validation.image-height-too-large";

}
