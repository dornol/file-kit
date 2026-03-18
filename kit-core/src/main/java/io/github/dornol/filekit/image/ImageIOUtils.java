package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Shared utility for writing images via ImageIO.
 * Used by {@link ImageIOResizer} and {@link ImageIOWatermarker}.
 */
final class ImageIOUtils {

    private ImageIOUtils() {}

    /**
     * Writes a {@link BufferedImage} to a byte array in the specified format and quality.
     *
     * <p>For JPEG output, the image is converted to RGB (no alpha channel) with a white background
     * to avoid rendering artifacts.</p>
     *
     * @param image   the image to write
     * @param format  target format name (e.g. "png", "jpeg")
     * @param quality compression quality between 0.0 (lowest) and 1.0 (highest);
     *                ignored if the format does not support compression
     * @return the encoded image bytes
     * @throws IOException           if an I/O error occurs during writing
     * @throws FileStorageException  if no {@link javax.imageio.ImageWriter} is available for the format
     */
    static byte[] writeImage(BufferedImage image, String format, float quality) throws IOException {
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
