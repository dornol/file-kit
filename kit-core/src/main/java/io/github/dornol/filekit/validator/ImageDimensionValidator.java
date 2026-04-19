package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;

/**
 * Validates image pixel dimensions by reading only the image header via
 * {@link ImageIO} (no full decode).
 *
 * <p>Extracted from {@link FileValidationHelper} so callers that only need
 * dimension validation can depend on this class directly.</p>
 *
 * @since 0.1.18
 */
public final class ImageDimensionValidator {

    private static final Logger log = LoggerFactory.getLogger(ImageDimensionValidator.class);

    /**
     * Validates image dimensions against the given constraints. Uses {@link ImageIO}
     * to read only the image header (width/height) without decoding the full image.
     *
     * @param value     the file to check
     * @param minWidth  minimum width in pixels (0 = no limit)
     * @param maxWidth  maximum width in pixels (0 = no limit)
     * @param minHeight minimum height in pixels (0 = no limit)
     * @param maxHeight maximum height in pixels (0 = no limit)
     * @return {@code null} if valid, or the message key for the failed check
     */
    public @Nullable String validate(FileSource value,
                                      int minWidth, int maxWidth,
                                      int minHeight, int maxHeight) {
        Objects.requireNonNull(value, "value");
        try (InputStream is = value.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) {
                log.debug("Unable to create image input stream for dimension validation");
                return ValidationMessageKeys.IMAGE_NOT_READABLE;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                log.debug("No suitable image reader found for dimension validation");
                return ValidationMessageKeys.IMAGE_NOT_READABLE;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (minWidth > 0 && width < minWidth) {
                    log.debug("Image width {} is below minimum {}", width, minWidth);
                    return ValidationMessageKeys.IMAGE_WIDTH_TOO_SMALL;
                }
                if (maxWidth > 0 && width > maxWidth) {
                    log.debug("Image width {} exceeds maximum {}", width, maxWidth);
                    return ValidationMessageKeys.IMAGE_WIDTH_TOO_LARGE;
                }
                if (minHeight > 0 && height < minHeight) {
                    log.debug("Image height {} is below minimum {}", height, minHeight);
                    return ValidationMessageKeys.IMAGE_HEIGHT_TOO_SMALL;
                }
                if (maxHeight > 0 && height > maxHeight) {
                    log.debug("Image height {} exceeds maximum {}", height, maxHeight);
                    return ValidationMessageKeys.IMAGE_HEIGHT_TOO_LARGE;
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("Failed to read image dimensions", e);
            return ValidationMessageKeys.IMAGE_NOT_READABLE;
        }
        return null;
    }

    /**
     * Validates image dimensions for each file. Returns on the first failure.
     *
     * @return {@code null} if all valid, or the message key for the first failed check
     */
    public @Nullable String validateAll(Iterable<? extends FileSource> files,
                                         int minWidth, int maxWidth,
                                         int minHeight, int maxHeight) {
        Objects.requireNonNull(files, "files");
        for (FileSource file : files) {
            String result = validate(file, minWidth, maxWidth, minHeight, maxHeight);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
