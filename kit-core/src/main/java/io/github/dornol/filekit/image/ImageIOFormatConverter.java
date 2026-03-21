package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Default {@link ImageFormatConverter} implementation using ImageIO.
 */
public class ImageIOFormatConverter implements ImageFormatConverter {

    /** Creates a format converter. */
    public ImageIOFormatConverter() {
    }

    @Override
    public ConvertResult convert(byte[] imageBytes, ConvertOption option) {
        try {
            BufferedImage image = ImageIOUtils.readImage(imageBytes);

            byte[] outputBytes = ImageIOUtils.writeImage(image, option.outputFormat(), option.quality());
            ImageMetadata metadata = new ImageMetadata(image.getWidth(), image.getHeight(), option.outputFormat());

            return new ConvertResult(outputBytes, metadata);
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Failed to convert image format", e);
        }
    }
}
