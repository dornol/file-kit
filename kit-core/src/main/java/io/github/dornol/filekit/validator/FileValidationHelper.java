package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * Helper that performs individual file validation checks against a {@link FileSource}.
 *
 * <p>Used by both core validators ({@link FileSourceValidator}) and Spring validators
 * to share validation logic.</p>
 *
 * <p>Since 0.1.18 this class is a thin facade. The heavy lifting lives in
 * {@link MediaTypeValidator} and {@link ImageDimensionValidator}; callers that
 * only need one of those concerns can depend on them directly. The facade is
 * retained for backward compatibility and for composite validation flows that
 * need several checks together.</p>
 */
public class FileValidationHelper {

    private static final Logger log = LoggerFactory.getLogger(FileValidationHelper.class);

    private final MediaTypeValidator mediaType;
    private final ImageDimensionValidator imageDim = new ImageDimensionValidator();

    /** @param detector media type detector for MIME type identification */
    public FileValidationHelper(MediaTypeDetector detector) {
        this.mediaType = new MediaTypeValidator(detector);
    }

    /**
     * Validates both media type and extension in a single pass, detecting MIME type only once.
     *
     * @param value   the file to check
     * @param allowed set of allowed media types
     * @return {@code null} if valid, or the message key for the failed check
     */
    public @Nullable String validateMediaTypeAndExtension(FileSource value, Set<SafeMediaType> allowed) {
        return mediaType.validate(value, allowed);
    }

    /**
     * Returns {@code true} if the file is empty.
     */
    public boolean isFileEmpty(FileSource value) {
        Objects.requireNonNull(value, "value");
        return value.isEmpty();
    }

    /**
     * Returns {@code true} if the file size exceeds the given maximum.
     *
     * @param value   the file to check
     * @param maxSize maximum size in bytes (0 = no limit)
     */
    public boolean isFileSizeExceeded(FileSource value, long maxSize) {
        Objects.requireNonNull(value, "value");
        if (maxSize > 0 && value.getSize() > maxSize) {
            log.debug("File size {} exceeds maximum {}", value.getSize(), maxSize);
            return true;
        }
        return false;
    }

    /**
     * Validates the filename for safety (null, blank, path traversal, length).
     *
     * @param value the file to check
     * @return {@code true} if the filename is safe
     */
    public boolean isValidFilename(FileSource value) {
        Objects.requireNonNull(value, "value");
        String name = value.getOriginalFilename();

        if (!FilenameValidator.isSafe(name)) {
            if (name != null && FilenameValidator.containsTraversalCharacters(name)) {
                log.warn("Potentially dangerous filename detected: '{}'", name);
            } else if (name != null && name.length() > FilenameValidator.MAX_FILENAME_LENGTH) {
                log.debug("Filename exceeds {} characters: length={}", FilenameValidator.MAX_FILENAME_LENGTH, name.length());
            } else {
                log.debug("Filename is null or blank");
            }
            return false;
        }

        return true;
    }

    /**
     * Validates image dimensions against the given constraints. Uses ImageIO to read
     * only the image header (width/height) without decoding the full image.
     *
     * @return {@code null} if valid, or the message key for the failed check
     */
    public @Nullable String validateImageDimensions(FileSource value,
                                                     int minWidth, int maxWidth,
                                                     int minHeight, int maxHeight) {
        return imageDim.validate(value, minWidth, maxWidth, minHeight, maxHeight);
    }

    /**
     * Validates image dimensions for each file in the iterable.
     *
     * @return {@code null} if all valid, or the message key for the first failed check
     */
    public @Nullable String validateAllImageDimensions(Iterable<? extends FileSource> files,
                                                        int minWidth, int maxWidth,
                                                        int minHeight, int maxHeight) {
        return imageDim.validateAll(files, minWidth, maxWidth, minHeight, maxHeight);
    }

    // --- Batch validation methods for array/collection validators ---

    /**
     * Returns {@code true} if any file in the iterable is empty.
     */
    public boolean isAnyFileEmpty(Iterable<? extends FileSource> files) {
        Objects.requireNonNull(files, "files");
        for (FileSource file : files) {
            if (isFileEmpty(file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any file in the iterable exceeds the given maximum size.
     */
    public boolean isAnyFileSizeExceeded(Iterable<? extends FileSource> files, long maxSize) {
        Objects.requireNonNull(files, "files");
        for (FileSource file : files) {
            if (isFileSizeExceeded(file, maxSize)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if all filenames in the iterable are valid.
     */
    public boolean isAllValidFilenames(Iterable<? extends FileSource> files) {
        Objects.requireNonNull(files, "files");
        for (FileSource file : files) {
            if (!isValidFilename(file)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates media type and extension for each file.
     *
     * @return {@code null} if all valid, or the message key for the first failed check
     */
    public @Nullable String validateAllMediaTypeAndExtension(Iterable<? extends FileSource> files,
                                                              Set<SafeMediaType> allowed) {
        return mediaType.validateAll(files, allowed);
    }

}
