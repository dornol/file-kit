package io.github.dornol.filekit.image;

/**
 * Result of an image rotation operation.
 *
 * <p><strong>Note:</strong> Because {@code byte[]} uses reference equality,
 * {@link #equals(Object)} and {@link #hashCode()} compare the array by identity,
 * not by content. Two instances with identical byte content are <em>not</em> equal
 * unless they share the same array reference.</p>
 *
 * @param data     the rotated image bytes
 * @param metadata metadata of the rotated image
 * @since 0.1.24
 */
public record RotateResult(byte[] data, ImageMetadata metadata) {
}
