package io.github.dornol.filekit.image;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Options for image resizing.
 *
 * @param targetWidth  desired width in pixels
 * @param targetHeight desired height in pixels
 * @param scaleMode    how to handle aspect ratio differences
 * @param outputFormat output image format (e.g. "png", "jpeg"); null to keep original format
 * @param quality      output quality (0.0 - 1.0), applicable to lossy formats like JPEG
 */
public record ResizeOption(
        int targetWidth,
        int targetHeight,
        ScaleMode scaleMode,
        @Nullable String outputFormat,
        float quality
) {

    private static final float DEFAULT_QUALITY = 0.85f;

    public ResizeOption {
        if (targetWidth <= 0) {
            throw new IllegalArgumentException("targetWidth must be positive: " + targetWidth);
        }
        if (targetHeight <= 0) {
            throw new IllegalArgumentException("targetHeight must be positive: " + targetHeight);
        }
        Objects.requireNonNull(scaleMode, "scaleMode");
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0: " + quality);
        }
    }

    /**
     * Creates a FIT resize option with default quality.
     */
    public static ResizeOption fit(int width, int height) {
        return new ResizeOption(width, height, ScaleMode.FIT, null, DEFAULT_QUALITY);
    }

    /**
     * Creates a COVER resize option with default quality.
     */
    public static ResizeOption cover(int width, int height) {
        return new ResizeOption(width, height, ScaleMode.COVER, null, DEFAULT_QUALITY);
    }

    /**
     * Creates an EXACT resize option with default quality.
     */
    public static ResizeOption exact(int width, int height) {
        return new ResizeOption(width, height, ScaleMode.EXACT, null, DEFAULT_QUALITY);
    }

    /**
     * Creates a FIT thumbnail option where both dimensions are the same max size.
     */
    public static ResizeOption thumbnail(int maxDimension) {
        return fit(maxDimension, maxDimension);
    }
}
