package io.github.dornol.filekit.image;

/**
 * SPI for applying watermarks to images.
 */
public interface ImageWatermarker {

    /**
     * Applies a watermark to the given image bytes.
     *
     * @param imageBytes the raw image content
     * @param option     watermark options
     * @return the result containing the watermarked image bytes and metadata
     * @throws io.github.dornol.filekit.storage.FileStorageException if watermarking fails
     */
    WatermarkResult apply(byte[] imageBytes, WatermarkOption option);
}
