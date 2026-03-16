package io.github.dornol.filekit.image;

/**
 * SPI for extracting metadata from image bytes.
 */
public interface ImageMetadataExtractor {

    /**
     * Extracts metadata from the given image bytes.
     *
     * @param imageBytes the raw image content
     * @return the extracted metadata
     * @throws io.github.dornol.filekit.storage.FileStorageException if extraction fails
     */
    ImageMetadata extract(byte[] imageBytes);
}
