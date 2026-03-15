package io.github.dornol.filekit.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class FileSourceValidatorHelper {

    private static final Logger log = LoggerFactory.getLogger(FileSourceValidatorHelper.class);

    private final MediaTypeDetector detector;

    public FileSourceValidatorHelper(MediaTypeDetector detector) {
        this.detector = detector;
    }

    public boolean isValidMediaType(FileSource value, Set<SafeMediaType> allowed) {
        String detected;

        try {
            detected = detector.detect(value.getOriginalFilename(), value.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        for (SafeMediaType type : allowed) {
            if (detected.equals(type.getMediaType())) {
                return true;
            }
        }

        log.warn("detected mime type: {} is not in the allowed list: {}", detected, allowed);

        return false;
    }

    public boolean isFileEmpty(FileSource value) {
        return value.isEmpty();
    }

    public boolean isFileSizeExceeded(FileSource value, long maxSize) {
        return maxSize > 0 && value.getSize() > maxSize;
    }

    public boolean isValidFilename(FileSource value) {
        String name = value.getOriginalFilename();

        if (name == null) {
            return false;
        }

        if (name.isBlank()) {
            return false;
        }

        if (name.length() > 200) {
            return false;
        }

        return !name.equals("..") && !name.contains("/") && !name.contains("\\");
    }

    public boolean isValidExtension(FileSource value, Set<SafeMediaType> allowed) {
        String originalFilename = value.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }

        String extension = getExtension(originalFilename).toLowerCase();
        if (extension.isEmpty()) {
            return false;
        }

        String detectedMime;
        try {
            detectedMime = detector.detect(originalFilename, value.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        for (SafeMediaType safe : allowed) {
            if (safe.getMediaType().equalsIgnoreCase(detectedMime)) {
                if (safe.getExtensions().contains(extension)) {
                    return true;
                }
            }
        }

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
