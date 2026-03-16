package io.github.dornol.filekit.image;

/**
 * Basic image metadata extracted from image bytes.
 *
 * @param width  image width in pixels
 * @param height image height in pixels
 * @param format image format name (e.g. "png", "jpeg")
 */
public record ImageMetadata(int width, int height, String format) {
}
