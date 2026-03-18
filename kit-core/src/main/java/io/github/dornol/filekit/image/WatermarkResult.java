package io.github.dornol.filekit.image;

/**
 * Result of a watermark operation.
 *
 * <p><strong>Note:</strong> Because {@code byte[]} uses reference equality,
 * {@link #equals(Object)} and {@link #hashCode()} compare the array by identity,
 * not by content. Two instances with identical byte content are <em>not</em> equal
 * unless they share the same array reference.</p>
 *
 * @param data     the watermarked image bytes
 * @param metadata metadata of the output image
 */
public record WatermarkResult(byte[] data, ImageMetadata metadata) {
}
