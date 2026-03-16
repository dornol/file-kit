package io.github.dornol.filekit.image;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Options for applying a watermark to an image.
 *
 * @param type         watermark type (TEXT or IMAGE)
 * @param text         text to overlay (required for TEXT type)
 * @param overlayImage overlay image bytes (required for IMAGE type)
 * @param position     watermark position on the image
 * @param opacity      watermark opacity (0.0 - 1.0)
 * @param fontName     font name for text watermarks (null for default)
 * @param fontSize     font size for text watermarks
 * @param outputFormat output image format (null to keep original)
 * @param quality      output quality (0.0 - 1.0)
 */
public record WatermarkOption(
        WatermarkType type,
        @Nullable String text,
        @Nullable byte[] overlayImage,
        WatermarkPosition position,
        float opacity,
        @Nullable String fontName,
        int fontSize,
        @Nullable String outputFormat,
        float quality
) {

    public enum WatermarkType { TEXT, IMAGE }

    private static final float DEFAULT_QUALITY = 0.85f;
    private static final int DEFAULT_FONT_SIZE = 24;
    private static final String DEFAULT_FONT_NAME = "SansSerif";

    public WatermarkOption {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        if (type == WatermarkType.TEXT && (text == null || text.isBlank())) {
            throw new IllegalArgumentException("text must not be blank for TEXT watermark");
        }
        if (type == WatermarkType.IMAGE && (overlayImage == null || overlayImage.length == 0)) {
            throw new IllegalArgumentException("overlayImage must not be empty for IMAGE watermark");
        }
        if (opacity < 0.0f || opacity > 1.0f) {
            throw new IllegalArgumentException("opacity must be between 0.0 and 1.0: " + opacity);
        }
        if (fontSize <= 0) {
            throw new IllegalArgumentException("fontSize must be positive: " + fontSize);
        }
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0: " + quality);
        }
    }

    /**
     * Creates a text watermark option with defaults.
     */
    public static WatermarkOption text(String text, WatermarkPosition position, float opacity) {
        return new WatermarkOption(WatermarkType.TEXT, text, null, position, opacity,
                DEFAULT_FONT_NAME, DEFAULT_FONT_SIZE, null, DEFAULT_QUALITY);
    }

    /**
     * Creates an image watermark option with defaults.
     */
    public static WatermarkOption image(byte[] overlay, WatermarkPosition position, float opacity) {
        return new WatermarkOption(WatermarkType.IMAGE, null, overlay, position, opacity,
                null, DEFAULT_FONT_SIZE, null, DEFAULT_QUALITY);
    }
}
