package io.github.dornol.filekit.image;

/**
 * Result of a watermark operation.
 *
 * @param data     the watermarked image bytes
 * @param metadata metadata of the output image
 */
public record WatermarkResult(byte[] data, ImageMetadata metadata) {
}
