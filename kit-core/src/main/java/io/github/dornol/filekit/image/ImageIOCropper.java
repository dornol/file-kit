package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Default {@link ImageCropper} implementation using {@link BufferedImage#getSubimage}
 * and ImageIO.
 *
 * @since 0.1.24
 */
public class ImageIOCropper implements ImageCropper {

    private final ImageMetadataExtractor metadataExtractor;

    /** Creates a cropper with the default {@link ImageIOMetadataExtractor}. */
    public ImageIOCropper() {
        this(new ImageIOMetadataExtractor());
    }

    /**
     * Creates a cropper with a custom metadata extractor.
     *
     * @param metadataExtractor extractor used to read output image metadata
     */
    public ImageIOCropper(ImageMetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @Override
    public CropResult crop(byte[] imageBytes, CropOption option) {
        try {
            BufferedImage source = ImageIOUtils.readImage(imageBytes);
            validateRegionInsideBounds(source, option);
            String outputFormat = ImageIOUtils.resolveOutputFormat(
                    option.outputFormat(), metadataExtractor, imageBytes);

            BufferedImage cropped = source.getSubimage(
                    option.x(), option.y(), option.width(), option.height());

            byte[] outputBytes = ImageIOUtils.writeImage(cropped, outputFormat, option.quality());
            ImageMetadata resultMeta = new ImageMetadata(
                    cropped.getWidth(), cropped.getHeight(), outputFormat);

            return new CropResult(outputBytes, resultMeta);
        } catch (FileStorageException | IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Failed to crop image", e);
        }
    }

    private static void validateRegionInsideBounds(BufferedImage source, CropOption option) {
        int imageW = source.getWidth();
        int imageH = source.getHeight();
        if (option.x() + option.width() > imageW || option.y() + option.height() > imageH) {
            throw new IllegalArgumentException(
                    "crop region exceeds image bounds: image=" + imageW + "x" + imageH
                            + ", region=(" + option.x() + "," + option.y() + ")+"
                            + option.width() + "x" + option.height());
        }
    }
}
