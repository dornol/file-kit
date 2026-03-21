package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Default {@link ImageResizer} implementation using Java Graphics2D and ImageIO.
 */
public class ImageIOResizer implements ImageResizer {

    private final ImageMetadataExtractor metadataExtractor;

    /** Creates a resizer with the default {@link ImageIOMetadataExtractor}. */
    public ImageIOResizer() {
        this(new ImageIOMetadataExtractor());
    }

    /**
     * Creates a resizer with a custom metadata extractor.
     *
     * @param metadataExtractor extractor used to read output image metadata
     */
    public ImageIOResizer(ImageMetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @Override
    public ResizeResult resize(byte[] imageBytes, ResizeOption option) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null) {
                throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                        "Unable to read image");
            }

            ImageMetadata sourceMeta = metadataExtractor.extract(imageBytes);
            String outputFormat = option.outputFormat() != null ? option.outputFormat() : sourceMeta.format();

            BufferedImage resized = applyResize(source, option);

            byte[] outputBytes = ImageIOUtils.writeImage(resized, outputFormat, option.quality());
            ImageMetadata resultMeta = new ImageMetadata(resized.getWidth(), resized.getHeight(), outputFormat);

            return new ResizeResult(outputBytes, resultMeta);
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Failed to resize image", e);
        }
    }

    private BufferedImage applyResize(BufferedImage source, ResizeOption option) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();

        return switch (option.scaleMode()) {
            case FIT -> resizeFit(source, srcW, srcH, option.targetWidth(), option.targetHeight());
            case COVER -> resizeCover(source, srcW, srcH, option.targetWidth(), option.targetHeight());
            case EXACT -> resizeExact(source, option.targetWidth(), option.targetHeight());
        };
    }

    private BufferedImage resizeFit(BufferedImage source, int srcW, int srcH, int targetW, int targetH) {
        double scale = Math.min((double) targetW / srcW, (double) targetH / srcH);
        int newW = (int) Math.round(srcW * scale);
        int newH = (int) Math.round(srcH * scale);
        return drawScaled(source, newW, newH);
    }

    private BufferedImage resizeCover(BufferedImage source, int srcW, int srcH, int targetW, int targetH) {
        double scale = Math.max((double) targetW / srcW, (double) targetH / srcH);
        int scaledW = Math.max(targetW, (int) Math.round(srcW * scale));
        int scaledH = Math.max(targetH, (int) Math.round(srcH * scale));
        BufferedImage scaled = drawScaled(source, scaledW, scaledH);

        // Center crop to target dimensions
        int cropX = (scaledW - targetW) / 2;
        int cropY = (scaledH - targetH) / 2;
        return scaled.getSubimage(cropX, cropY, targetW, targetH);
    }

    private BufferedImage resizeExact(BufferedImage source, int targetW, int targetH) {
        return drawScaled(source, targetW, targetH);
    }

    private BufferedImage drawScaled(BufferedImage source, int width, int height) {
        int imageType = source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType();
        BufferedImage result = new BufferedImage(width, height, imageType);
        Graphics2D g2d = result.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(source, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }
        return result;
    }

}
