package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

    public ImageIOExifStripper() {
        this(new ImageIOMetadataExtractor());
    }

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
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                        "Unable to read image for EXIF stripping");
            }
            return ImageIOUtils.writeImage(image, metadata.format(), quality);
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Failed to strip EXIF metadata", e);
        }
    }
}
