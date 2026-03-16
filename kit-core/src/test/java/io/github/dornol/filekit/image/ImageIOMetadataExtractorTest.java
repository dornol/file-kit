package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageIOMetadataExtractorTest {

    private final ImageIOMetadataExtractor extractor = new ImageIOMetadataExtractor();

    @Nested
    class PngFormat {

        @Test
        void extractsLandscapeImage() throws IOException {
            byte[] imageBytes = createTestImage(320, 240, "png");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(320, metadata.width());
            assertEquals(240, metadata.height());
            assertEquals("png", metadata.format());
        }

        @Test
        void extractsPortraitImage() throws IOException {
            byte[] imageBytes = createTestImage(240, 320, "png");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(240, metadata.width());
            assertEquals(320, metadata.height());
            assertEquals("png", metadata.format());
        }

        @Test
        void extractsSquareImage() throws IOException {
            byte[] imageBytes = createTestImage(100, 100, "png");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(100, metadata.width());
            assertEquals(100, metadata.height());
            assertEquals("png", metadata.format());
        }

        @Test
        void extractsSinglePixelImage() throws IOException {
            byte[] imageBytes = createTestImage(1, 1, "png");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(1, metadata.width());
            assertEquals(1, metadata.height());
        }

        @Test
        void extractsLargeImage() throws IOException {
            byte[] imageBytes = createTestImage(4000, 3000, "png");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(4000, metadata.width());
            assertEquals(3000, metadata.height());
        }
    }

    @Nested
    class JpegFormat {

        @Test
        void extractsJpegMetadata() throws IOException {
            byte[] imageBytes = createTestImage(640, 480, "jpeg");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(640, metadata.width());
            assertEquals(480, metadata.height());
            assertEquals("jpeg", metadata.format());
        }
    }

    @Nested
    class GifFormat {

        @Test
        void extractsGifMetadata() throws IOException {
            byte[] imageBytes = createTestImage(50, 50, "gif");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(50, metadata.width());
            assertEquals(50, metadata.height());
            assertEquals("gif", metadata.format());
        }
    }

    @Nested
    class BmpFormat {

        @Test
        void extractsBmpMetadata() throws IOException {
            byte[] imageBytes = createTestImage(200, 150, "bmp");

            ImageMetadata metadata = extractor.extract(imageBytes);

            assertEquals(200, metadata.width());
            assertEquals(150, metadata.height());
            assertEquals("bmp", metadata.format());
        }
    }

    @Nested
    class ImageTypes {

        @Test
        void extractsArgbImage() throws IOException {
            BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB);
            byte[] bytes = writeImage(image, "png");

            ImageMetadata metadata = extractor.extract(bytes);

            assertEquals(100, metadata.width());
            assertEquals(80, metadata.height());
        }

        @Test
        void extractsGrayscaleImage() throws IOException {
            BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_BYTE_GRAY);
            byte[] bytes = writeImage(image, "png");

            ImageMetadata metadata = extractor.extract(bytes);

            assertEquals(60, metadata.width());
            assertEquals(40, metadata.height());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void invalidBytes_throws() {
            byte[] invalid = "not an image".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(invalid));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void emptyBytes_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(new byte[0]));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void randomBytes_throws() {
            byte[] random = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(random));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void truncatedPngHeader_throws() {
            // PNG header starts with 0x89 0x50 0x4E 0x47, but this is incomplete
            byte[] truncated = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(truncated));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }
    }

    // ── Test helpers ────────────────────────────────────────────────

    static byte[] createTestImage(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Draw some content to ensure non-trivial image
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width / 2, height / 2);
        g2d.dispose();
        return writeImage(image, format);
    }

    static byte[] writeImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}
