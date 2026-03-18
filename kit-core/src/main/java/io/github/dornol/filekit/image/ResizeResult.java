package io.github.dornol.filekit.image;

/**
 * Result of an image resize operation.
 *
 * <p><strong>Note:</strong> Because {@code byte[]} uses reference equality,
 * {@link #equals(Object)} and {@link #hashCode()} compare the array by identity,
 * not by content. Two instances with identical byte content are <em>not</em> equal
 * unless they share the same array reference.</p>
 *
 * @param data     the resized image bytes
 * @param metadata metadata of the resized image
 */
public record ResizeResult(byte[] data, ImageMetadata metadata) {
}
