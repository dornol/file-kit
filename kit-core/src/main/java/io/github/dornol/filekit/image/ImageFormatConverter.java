package io.github.dornol.filekit.image;

/**
 * SPI for converting images between formats.
 */
public interface ImageFormatConverter {

    /**
     * Converts an image to a different format.
     *
     * @param imageBytes the original image bytes
     * @param option     conversion options (target format, quality)
     * @return the converted image data and metadata
     */
    ConvertResult convert(byte[] imageBytes, ConvertOption option);
}
