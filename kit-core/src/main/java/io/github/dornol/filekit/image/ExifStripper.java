package io.github.dornol.filekit.image;

/**
 * SPI for stripping EXIF and other metadata from images.
 */
public interface ExifStripper {

    /**
     * Strips EXIF metadata from image bytes using default quality (0.95).
     *
     * @param imageBytes the original image bytes
     * @return image bytes without EXIF metadata
     */
    byte[] strip(byte[] imageBytes);

    /**
     * Strips EXIF metadata from image bytes with the specified quality.
     *
     * @param imageBytes the original image bytes
     * @param quality    output quality (0.0 - 1.0), applicable to lossy formats
     * @return image bytes without EXIF metadata
     */
    byte[] strip(byte[] imageBytes, float quality);
}
