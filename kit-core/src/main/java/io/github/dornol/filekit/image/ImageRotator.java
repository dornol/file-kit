package io.github.dornol.filekit.image;

/**
 * SPI for rotating images by 90° multiples.
 *
 * @since 0.1.24
 */
public interface ImageRotator {

    /**
     * Rotates the given image bytes according to the specified options.
     *
     * @param imageBytes the raw image content
     * @param option     rotate options
     * @return the rotate result containing the new image bytes and metadata
     * @throws io.github.dornol.filekit.storage.FileStorageException if rotation fails
     */
    RotateResult rotate(byte[] imageBytes, RotateOption option);
}
