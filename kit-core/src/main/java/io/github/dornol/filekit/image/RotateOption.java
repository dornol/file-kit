package io.github.dornol.filekit.image;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Options for image rotation.
 *
 * @param angle        rotation angle (90/180/270)
 * @param outputFormat output image format (e.g. "png", "jpeg"); {@code null} to keep original format
 * @param quality      output quality (0.0 - 1.0), applicable to lossy formats like JPEG
 * @since 0.1.24
 */
public record RotateOption(
        RotateAngle angle,
        @Nullable String outputFormat,
        float quality
) {

    private static final float DEFAULT_QUALITY = 0.85f;

    public RotateOption {
        Objects.requireNonNull(angle, "angle");
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0: " + quality);
        }
    }

    /** Creates a rotate option with default quality, keeping the original format. */
    public static RotateOption of(RotateAngle angle) {
        return new RotateOption(angle, null, DEFAULT_QUALITY);
    }
}
