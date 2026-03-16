package io.github.dornol.filekit.image;

/**
 * SPI for resizing images.
 */
public interface ImageResizer {

    /**
     * Resizes the given image bytes according to the specified options.
     *
     * @param imageBytes the raw image content
     * @param option     resize options
     * @return the resize result containing the new image bytes and metadata
     * @throws io.github.dornol.filekit.storage.FileStorageException if resizing fails
     */
    ResizeResult resize(byte[] imageBytes, ResizeOption option);
}
