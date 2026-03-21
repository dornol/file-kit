package io.github.dornol.filekit.image;

import java.util.Objects;

/**
 * Basic image metadata extracted from image bytes.
 *
 * @param width  image width in pixels
 * @param height image height in pixels
 * @param format image format name (e.g. "png", "jpeg")
 */
public record ImageMetadata(int width, int height, String format) {
    public ImageMetadata {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
        Objects.requireNonNull(format, "format");
    }
}
