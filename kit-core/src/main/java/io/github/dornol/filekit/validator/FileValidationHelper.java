package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Helper that performs individual file validation checks against a {@link FileSource}.
 *
 * <p>Used by both core validators ({@link FileSourceValidator}) and
 * Spring validators to share validation logic.</p>
 */
public class FileValidationHelper {

    private static final Logger log = LoggerFactory.getLogger(FileValidationHelper.class);

    private final MediaTypeDetector detector;

    /** @param detector media type detector for MIME type identification */
    public FileValidationHelper(MediaTypeDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    /**
     * Validates both media type and extension in a single pass, detecting MIME type only once.
     *
     * @param value   the file to check
     * @param allowed set of allowed media types
     * @return {@code null} if valid, or the message key for the failed check
     */
    public @Nullable String validateMediaTypeAndExtension(FileSource value, Set<SafeMediaType> allowed) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(allowed, "allowed");
        String originalFilename = value.getOriginalFilename();

        String detected;
        try {
            detected = detector.detect(originalFilename, value.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to detect media type", e);
        }

        // Check media type
        boolean typeValid = false;
        for (SafeMediaType type : allowed) {
            if (detected.equals(type.getMediaType())) {
                typeValid = true;
                break;
            }
        }
        if (!typeValid) {
            log.warn("Detected media type '{}' is not in the allowed list: {}", detected, allowed);
            return "file-kit.validation.unsupported-media-type";
        }

        // Check extension
        if (originalFilename == null) {
            return "file-kit.validation.invalid-extension";
        }
        String extension = getExtension(originalFilename).toLowerCase(Locale.ENGLISH);
        if (extension.isEmpty()) {
            log.debug("File has no extension: '{}'", originalFilename);
            return "file-kit.validation.invalid-extension";
        }

        for (SafeMediaType safe : allowed) {
            if (safe.getMediaType().equalsIgnoreCase(detected) && safe.getExtensions().contains(extension)) {
                return null;
            }
        }

        log.debug("Extension '{}' does not match detected media type '{}'", extension, detected);
        return "file-kit.validation.invalid-extension";
    }

    /**
     * Checks whether the detected media type of the file is in the allowed set.
     *
     * @param value   the file to check
     * @param allowed set of allowed media types
     * @return {@code true} if the media type is allowed
     */
    public boolean isValidMediaType(FileSource value, Set<SafeMediaType> allowed) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(allowed, "allowed");
        String detected;

        try {
            detected = detector.detect(value.getOriginalFilename(), value.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to detect media type", e);
        }

        for (SafeMediaType type : allowed) {
            if (detected.equals(type.getMediaType())) {
                return true;
            }
        }

        log.warn("Detected media type '{}' is not in the allowed list: {}", detected, allowed);

        return false;
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
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(allowed, "allowed");
        for (FileSource file : files) {
            String result = validateMediaTypeAndExtension(file, allowed);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

}
