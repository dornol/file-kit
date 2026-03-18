package io.github.dornol.filekit.image;

/**
 * Result of an image format conversion.
 *
 * <p><strong>Note:</strong> Because {@code byte[]} uses reference equality,
 * {@link #equals(Object)} and {@link #hashCode()} compare the array by identity,
 * not by content. Two instances with identical byte content are <em>not</em> equal
 * unless they share the same array reference.</p>
 *
 * @param data     the converted image bytes
 * @param metadata metadata of the converted image
 */
public record ConvertResult(byte[] data, ImageMetadata metadata) {
}
