package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Default {@link ImageFormatConverter} implementation using ImageIO.
 */
public class ImageIOFormatConverter implements ImageFormatConverter {

    private final ImageMetadataExtractor metadataExtractor;

    public ImageIOFormatConverter() {
        this(new ImageIOMetadataExtractor());
    }

    public ImageIOFormatConverter(ImageMetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @Override
    public ConvertResult convert(byte[] imageBytes, ConvertOption option) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                        "Unable to read image for format conversion");
            }

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
