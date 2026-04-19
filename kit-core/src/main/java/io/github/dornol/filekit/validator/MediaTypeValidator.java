package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Validates a file's detected media type and extension against an allow-list in a
 * single detection pass.
 *
 * <p>Extracted from {@link FileValidationHelper} so callers that only need media
 * type / extension validation can depend on this class directly.</p>
 *
 * @since 0.1.18
 */
public final class MediaTypeValidator {

    private static final Logger log = LoggerFactory.getLogger(MediaTypeValidator.class);

    private final MediaTypeDetector detector;

    /**
     * @param detector media type detector for MIME type identification
     * @throws NullPointerException if {@code detector} is null
     */
    public MediaTypeValidator(MediaTypeDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    /**
     * Validates media type and extension in a single pass, detecting MIME type only once.
     *
     * @param value   the file to check
     * @param allowed set of allowed media types
     * @return {@code null} if valid, or the message key for the failed check
     */
    public @Nullable String validate(FileSource value, Set<SafeMediaType> allowed) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(allowed, "allowed");
        String originalFilename = value.getOriginalFilename();

        String detected;
        try (InputStream is = value.getInputStream()) {
            detected = detector.detect(originalFilename, is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to detect media type", e);
        }

        boolean typeValid = false;
        for (SafeMediaType type : allowed) {
            if (detected.equals(type.getMediaType())) {
                typeValid = true;
                break;
            }
        }
        if (!typeValid) {
            log.warn("Detected media type '{}' is not in the allowed list: {}", detected, allowed);
            return ValidationMessageKeys.UNSUPPORTED_MEDIA_TYPE;
        }

        if (originalFilename == null) {
            return ValidationMessageKeys.INVALID_EXTENSION;
        }
        String extension = getExtension(originalFilename).toLowerCase(Locale.ENGLISH);
        if (extension.isEmpty()) {
            log.debug("File has no extension: '{}'", originalFilename);
            return ValidationMessageKeys.INVALID_EXTENSION;
        }

        for (SafeMediaType safe : allowed) {
            if (safe.getMediaType().equalsIgnoreCase(detected) && safe.getExtensions().contains(extension)) {
                return null;
            }
        }

        log.debug("Extension '{}' does not match detected media type '{}'", extension, detected);
        return ValidationMessageKeys.INVALID_EXTENSION;
    }

    /**
     * Validates media type and extension for each file. Returns on the first failure.
     *
     * @return {@code null} if all valid, or the message key for the first failed check
     */
    public @Nullable String validateAll(Iterable<? extends FileSource> files, Set<SafeMediaType> allowed) {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(allowed, "allowed");
        for (FileSource file : files) {
            String result = validate(file, allowed);
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
