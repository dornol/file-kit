package io.github.dornol.filekit.image;

import org.jspecify.annotations.Nullable;

/**
 * Options for image cropping. Coordinates are in pixels; the crop region
 * is {@code (x, y)} to {@code (x + width, y + height)}.
 *
 * <p>Boundary check (region inside source image) is performed at
 * {@link ImageCropper#crop} time — the constructor only validates that the
 * values are individually well-formed.</p>
 *
 * @param x            left offset in pixels (must be &gt;= 0)
 * @param y            top offset in pixels (must be &gt;= 0)
 * @param width        crop width in pixels (must be &gt; 0)
 * @param height       crop height in pixels (must be &gt; 0)
 * @param outputFormat output image format (e.g. "png", "jpeg"); {@code null} to keep original format
 * @param quality      output quality (0.0 - 1.0), applicable to lossy formats like JPEG
 * @since 0.1.24
 */
public record CropOption(
        int x,
        int y,
        int width,
        int height,
        @Nullable String outputFormat,
        float quality
) {

    private static final float DEFAULT_QUALITY = 0.85f;

    public CropOption {
        if (x < 0) {
            throw new IllegalArgumentException("x must be non-negative: " + x);
        }
        if (y < 0) {
            throw new IllegalArgumentException("y must be non-negative: " + y);
        }
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0: " + quality);
        }
    }

    /** Creates a crop option with default quality, keeping the original format. */
    public static CropOption of(int x, int y, int width, int height) {
        return new CropOption(x, y, width, height, null, DEFAULT_QUALITY);
    }
}
