package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Default {@link ImageWatermarker} implementation using Java Graphics2D and ImageIO.
 */
public class ImageIOWatermarker implements ImageWatermarker {

    private static final int TILE_SPACING = 50;
    private static final int PADDING = 10;

    private final ImageMetadataExtractor metadataExtractor;

    public ImageIOWatermarker() {
        this(new ImageIOMetadataExtractor());
    }

    public ImageIOWatermarker(ImageMetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @Override
    public WatermarkResult apply(byte[] imageBytes, WatermarkOption option) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null) {
                throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                        "Unable to read image");
            }

            ImageMetadata sourceMeta = metadataExtractor.extract(imageBytes);
            String outputFormat = option.outputFormat() != null ? option.outputFormat() : sourceMeta.format();

            // Create a copy to draw on
            BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(),
                    source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType());
            Graphics2D g2d = result.createGraphics();
            try {
                g2d.drawImage(source, 0, 0, null);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, option.opacity()));

                if (option.type() == WatermarkOption.WatermarkType.TEXT) {
                    applyTextWatermark(g2d, result.getWidth(), result.getHeight(), option);
                } else {
                    applyImageWatermark(g2d, result.getWidth(), result.getHeight(), option);
                }
            } finally {
                g2d.dispose();
            }

            byte[] outputBytes = ImageIOUtils.writeImage(result, outputFormat, option.quality());
            ImageMetadata resultMeta = new ImageMetadata(result.getWidth(), result.getHeight(), outputFormat);

            return new WatermarkResult(outputBytes, resultMeta);
        } catch (FileStorageException e) {
            throw e;
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Failed to apply watermark", e);
        }
    }

    private void applyTextWatermark(Graphics2D g2d, int imgW, int imgH, WatermarkOption option) {
        String fontName = option.fontName() != null ? option.fontName() : "SansSerif";
        Font font = new Font(fontName, Font.BOLD, option.fontSize());
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        FontMetrics fm = g2d.getFontMetrics();
        int textW = fm.stringWidth(option.text());
        int textH = fm.getHeight();

        if (option.position() == WatermarkPosition.TILED) {
            for (int y = 0; y < imgH; y += textH + TILE_SPACING) {
                for (int x = 0; x < imgW; x += textW + TILE_SPACING) {
                    g2d.drawString(option.text(), x, y + fm.getAscent());
                }
            }
        } else {
            int[] pos = calculatePosition(option.position(), imgW, imgH, textW, textH);
            g2d.drawString(option.text(), pos[0], pos[1] + fm.getAscent());
        }
    }

    private void applyImageWatermark(Graphics2D g2d, int imgW, int imgH, WatermarkOption option) throws IOException {
        BufferedImage overlay = ImageIO.read(new ByteArrayInputStream(option.overlayImage()));
        if (overlay == null) {
            throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                    "Unable to read overlay image");
        }

        int overlayW = overlay.getWidth();
        int overlayH = overlay.getHeight();

        if (option.position() == WatermarkPosition.TILED) {
            for (int y = 0; y < imgH; y += overlayH + TILE_SPACING) {
                for (int x = 0; x < imgW; x += overlayW + TILE_SPACING) {
                    g2d.drawImage(overlay, x, y, null);
                }
            }
        } else {
            int[] pos = calculatePosition(option.position(), imgW, imgH, overlayW, overlayH);
            g2d.drawImage(overlay, pos[0], pos[1], null);
        }
    }

    private int[] calculatePosition(WatermarkPosition position, int imgW, int imgH, int wmW, int wmH) {
        return switch (position) {
            case CENTER -> new int[]{(imgW - wmW) / 2, (imgH - wmH) / 2};
            case TOP_LEFT -> new int[]{PADDING, PADDING};
            case TOP_RIGHT -> new int[]{imgW - wmW - PADDING, PADDING};
            case BOTTOM_LEFT -> new int[]{PADDING, imgH - wmH - PADDING};
            case BOTTOM_RIGHT -> new int[]{imgW - wmW - PADDING, imgH - wmH - PADDING};
            case TILED -> new int[]{0, 0}; // handled separately
        };
    }
}
