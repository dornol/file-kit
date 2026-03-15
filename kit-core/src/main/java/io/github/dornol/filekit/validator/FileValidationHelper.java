package io.github.dornol.filekit.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * Helper that performs individual file validation checks against a {@link FileSource}.
 *
 * <p>Used by both core validators ({@link FileSourceValidator}) and
 * Spring validators ({@link io.github.dornol.filekit.spring.validator.MultipartFileValidator})
 * to share validation logic.</p>
 */
public class FileValidationHelper {

    private static final Logger log = LoggerFactory.getLogger(FileValidationHelper.class);

    private final MediaTypeDetector detector;

    public FileValidationHelper(MediaTypeDetector detector) {
        this.detector = detector;
    }

    /**
     * Checks whether the detected media type of the file is in the allowed set.
     *
     * @param value   the file to check
     * @param allowed set of allowed media types
     * @return {@code true} if the media type is allowed
     */
    public boolean isValidMediaType(FileSource value, Set<SafeMediaType> allowed) {
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
        return value.isEmpty();
    }

    /**
     * Returns {@code true} if the file size exceeds the given maximum.
     *
     * @param value   the file to check
     * @param maxSize maximum size in bytes (0 = no limit)
     */
    public boolean isFileSizeExceeded(FileSource value, long maxSize) {
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
        String name = value.getOriginalFilename();

        if (name == null || name.isBlank()) {
            log.debug("Filename is null or blank");
            return false;
        }

        if (name.length() > 200) {
            log.debug("Filename exceeds 200 characters: length={}", name.length());
            return false;
        }

        if (name.equals("..") || name.contains("/") || name.contains("\\")) {
            log.warn("Potentially dangerous filename detected: '{}'", name);
            return false;
        }

        return true;
    }

    /**
     * Validates that the file extension matches the detected content type
     * within the allowed media type set.
     *
     * @param value   the file to check
     * @param allowed set of allowed media types to match against
     * @return {@code true} if the extension is consistent with the detected type
     */
    public boolean isValidExtension(FileSource value, Set<SafeMediaType> allowed) {
        String originalFilename = value.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }

        String extension = getExtension(originalFilename).toLowerCase();
        if (extension.isEmpty()) {
            log.debug("File has no extension: '{}'", originalFilename);
            return false;
        }

        String detectedMime;
        try {
            detectedMime = detector.detect(originalFilename, value.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to detect media type", e);
        }

        for (SafeMediaType safe : allowed) {
            if (safe.getMediaType().equalsIgnoreCase(detectedMime)) {
                if (safe.getExtensions().contains(extension)) {
                    return true;
                }
            }
        }

        log.debug("Extension '{}' does not match detected media type '{}'", extension, detectedMime);
        return false;
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
