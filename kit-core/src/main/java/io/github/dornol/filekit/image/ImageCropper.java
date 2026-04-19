package io.github.dornol.filekit.image;

/**
 * SPI for cropping images to a pixel-coordinate rectangle.
 *
 * @since 0.1.24
 */
public interface ImageCropper {

    /**
     * Crops the given image bytes according to the specified region.
     *
     * @param imageBytes the raw image content
     * @param option     crop options (x/y/width/height + output format/quality)
     * @return the crop result containing the new image bytes and metadata
     * @throws IllegalArgumentException if the crop region exceeds the image bounds
     * @throws io.github.dornol.filekit.storage.FileStorageException if cropping fails
     */
    CropResult crop(byte[] imageBytes, CropOption option);
}
