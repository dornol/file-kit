package io.github.dornol.filekit.image;

/**
 * SPI for generating image thumbnails.
 */
public interface ThumbnailGenerator {

    /**
     * Generates a thumbnail from the given image bytes.
     *
     * @param imageBytes the raw image content
     * @param option     thumbnail options
     * @return the resize result containing the thumbnail bytes and metadata
     * @throws io.github.dornol.filekit.storage.FileStorageException if thumbnail generation fails
     */
    ResizeResult generate(byte[] imageBytes, ThumbnailOption option);
}
