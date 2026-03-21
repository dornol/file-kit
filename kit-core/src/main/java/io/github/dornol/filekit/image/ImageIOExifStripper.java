package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Default {@link ExifStripper} implementation using ImageIO.
 *
 * <p>Strips EXIF metadata by re-encoding the image through ImageIO,
 * which discards all metadata by default.</p>
 */
public class ImageIOExifStripper implements ExifStripper {

    private static final float DEFAULT_QUALITY = 0.95f;

    private final ImageMetadataExtractor metadataExtractor;

    /** Creates an EXIF stripper with the default {@link ImageIOMetadataExtractor}. */
    public ImageIOExifStripper() {
        this(new ImageIOMetadataExtractor());
    }

    /**
     * Creates an EXIF stripper with a custom metadata extractor.
     *
     * @param metadataExtractor extractor used to read output image metadata
     */
    public ImageIOExifStripper(ImageMetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @Override
    public byte[] strip(byte[] imageBytes) {
        return strip(imageBytes, DEFAULT_QUALITY);
    }

    @Override
    public byte[] strip(byte[] imageBytes, float quality) {
        try {
            ImageMetadata metadata = metadataExtractor.extract(imageBytes);
            BufferedImage image = ImageIOUtils.readImage(imageBytes);
            return ImageIOUtils.writeImage(image, metadata.format(), quality);
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Failed to strip EXIF metadata", e);
        }
    }
}
