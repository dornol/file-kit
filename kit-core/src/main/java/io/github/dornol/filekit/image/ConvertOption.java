package io.github.dornol.filekit.image;

import java.util.Objects;

/**
 * Options for image format conversion.
 *
 * @param outputFormat target image format (e.g. "png", "jpeg")
 * @param quality      output quality (0.0 - 1.0), applicable to lossy formats
 */
public record ConvertOption(String outputFormat, float quality) {

    private static final float DEFAULT_QUALITY = 0.85f;

    public ConvertOption {
        Objects.requireNonNull(outputFormat, "outputFormat");
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0: " + quality);
        }
    }

    /**
     * Creates a convert option with default quality.
     */
    public static ConvertOption of(String outputFormat) {
        return new ConvertOption(outputFormat, DEFAULT_QUALITY);
    }

    /**
     * Creates a convert option with the specified quality.
     */
    public static ConvertOption of(String outputFormat, float quality) {
        return new ConvertOption(outputFormat, quality);
    }
}
