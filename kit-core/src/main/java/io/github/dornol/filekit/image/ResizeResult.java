package io.github.dornol.filekit.image;

/**
 * Result of an image resize operation.
 *
 * @param data     the resized image bytes
 * @param metadata metadata of the resized image
 */
public record ResizeResult(byte[] data, ImageMetadata metadata) {
}
