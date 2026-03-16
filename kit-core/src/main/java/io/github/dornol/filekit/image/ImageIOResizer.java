package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Default {@link ImageResizer} implementation using Java Graphics2D and ImageIO.
 */
public class ImageIOResizer implements ImageResizer {

    private final ImageMetadataExtractor metadataExtractor;

    public ImageIOResizer() {
        this(new ImageIOMetadataExtractor());
    }

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

            byte[] outputBytes = writeImage(resized, outputFormat, option.quality());
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
        int scaledW = (int) Math.round(srcW * scale);
        int scaledH = (int) Math.round(srcH * scale);
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

    private byte[] writeImage(BufferedImage image, String format, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "No image writer found for format: " + format);
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0) {
                    param.setCompressionType(types[0]);
                }
                param.setCompressionQuality(quality);
            }

            // For JPEG, ensure we use RGB (no alpha channel)
            BufferedImage output = image;
            if ("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format)) {
                if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                    output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = output.createGraphics();
                    try {
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, image.getWidth(), image.getHeight());
                        g.drawImage(image, 0, 0, null);
                    } finally {
                        g.dispose();
                    }
                }
            }

            writer.write(null, new IIOImage(output, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}
