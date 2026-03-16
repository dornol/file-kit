package io.github.dornol.filekit.image;

import org.jspecify.annotations.Nullable;

/**
 * Options for thumbnail generation.
 *
 * @param maxDimension maximum width or height in pixels
 * @param outputFormat output image format (null to keep original)
 * @param quality      output quality (0.0 - 1.0)
 */
public record ThumbnailOption(int maxDimension, @Nullable String outputFormat, float quality) {

    private static final float DEFAULT_QUALITY = 0.8f;
    private static final int DEFAULT_MAX_DIMENSION = 200;

    public ThumbnailOption {
        if (maxDimension <= 0) {
            throw new IllegalArgumentException("maxDimension must be positive: " + maxDimension);
        }
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0: " + quality);
        }
    }

    /**
     * Creates a default thumbnail option (200px, 0.8 quality).
     */
    public static ThumbnailOption defaults() {
        return new ThumbnailOption(DEFAULT_MAX_DIMENSION, null, DEFAULT_QUALITY);
    }

    /**
     * Creates a thumbnail option with the specified max dimension.
     */
    public static ThumbnailOption ofSize(int maxDimension) {
        return new ThumbnailOption(maxDimension, null, DEFAULT_QUALITY);
    }
}
