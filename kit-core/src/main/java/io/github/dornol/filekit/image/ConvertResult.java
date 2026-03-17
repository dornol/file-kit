package io.github.dornol.filekit.image;

/**
 * Result of an image format conversion.
 *
 * @param data     the converted image bytes
 * @param metadata metadata of the converted image
 */
public record ConvertResult(byte[] data, ImageMetadata metadata) {
}
