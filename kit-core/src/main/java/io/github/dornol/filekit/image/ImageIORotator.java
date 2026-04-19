package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Default {@link ImageRotator} implementation using Java Graphics2D and ImageIO.
 *
 * @since 0.1.24
 */
public class ImageIORotator implements ImageRotator {

    private final ImageMetadataExtractor metadataExtractor;

    /** Creates a rotator with the default {@link ImageIOMetadataExtractor}. */
    public ImageIORotator() {
        this(new ImageIOMetadataExtractor());
    }

    /**
     * Creates a rotator with a custom metadata extractor.
     *
     * @param metadataExtractor extractor used to read output image metadata
     */
    public ImageIORotator(ImageMetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @Override
    public RotateResult rotate(byte[] imageBytes, RotateOption option) {
        try {
            BufferedImage source = ImageIOUtils.readImage(imageBytes);
            String outputFormat = ImageIOUtils.resolveOutputFormat(
                    option.outputFormat(), metadataExtractor, imageBytes);

            BufferedImage rotated = applyRotate(source, option.angle());

            byte[] outputBytes = ImageIOUtils.writeImage(rotated, outputFormat, option.quality());
            ImageMetadata resultMeta = new ImageMetadata(
                    rotated.getWidth(), rotated.getHeight(), outputFormat);

            return new RotateResult(outputBytes, resultMeta);
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Failed to rotate image", e);
        }
    }

    private BufferedImage applyRotate(BufferedImage source, RotateAngle angle) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        boolean swap = angle != RotateAngle.DEGREES_180;
        int outW = swap ? srcH : srcW;
        int outH = swap ? srcW : srcH;

        int imageType = source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType();
        BufferedImage result = new BufferedImage(outW, outH, imageType);
        Graphics2D g = result.createGraphics();
        try {
            AffineTransform tx = new AffineTransform();
            tx.translate(outW / 2.0, outH / 2.0);
            tx.rotate(Math.toRadians(angle.degrees()));
            tx.translate(-srcW / 2.0, -srcH / 2.0);
            g.drawImage(source, tx, null);
        } finally {
            g.dispose();
        }
        return result;
    }
}
