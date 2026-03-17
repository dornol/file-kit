package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageIOExifStripperTest {

    ImageIOExifStripper stripper;

    @BeforeEach
    void setUp() {
        stripper = new ImageIOExifStripper();
    }

    private byte[] createTestImage(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private byte[] createArgbImage(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(255, 0, 0, 128));
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    // ── Strip and preserve ──────────────────────────────────────────

    @Nested
    class StripAndPreserve {

        @Test
        void strippedImageIsReadable() throws IOException {
            byte[] original = createTestImage("png", 100, 80);
            byte[] stripped = stripper.strip(original);

            assertNotNull(stripped);
            assertTrue(stripped.length > 0);
        }

        @Test
        void preservesDimensions_landscape() throws IOException {
            byte[] original = createTestImage("png", 200, 100);
            byte[] stripped = stripper.strip(original);

            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals(200, meta.width());
            assertEquals(100, meta.height());
        }

        @Test
        void preservesDimensions_portrait() throws IOException {
            byte[] original = createTestImage("png", 100, 200);
            byte[] stripped = stripper.strip(original);

            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals(100, meta.width());
            assertEquals(200, meta.height());
        }

        @Test
        void preservesDimensions_square() throws IOException {
            byte[] original = createTestImage("jpeg", 150, 150);
            byte[] stripped = stripper.strip(original);

            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals(150, meta.width());
            assertEquals(150, meta.height());
        }

        @Test
        void preservesDimensions_singlePixel() throws IOException {
            byte[] original = createTestImage("png", 1, 1);
            byte[] stripped = stripper.strip(original);

            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals(1, meta.width());
            assertEquals(1, meta.height());
        }

        @Test
        void defaultQualityIsUsed() throws IOException {
            byte[] original = createTestImage("jpeg", 100, 100);
            byte[] strippedDefault = stripper.strip(original);
            byte[] stripped095 = stripper.strip(original, 0.95f);

            // Default quality should produce same-sized output as explicit 0.95
            assertNotNull(strippedDefault);
            assertNotNull(stripped095);
            // Both should be close in size (same quality setting)
            assertTrue(Math.abs(strippedDefault.length - stripped095.length) < strippedDefault.length * 0.1);
        }
    }

    // ── Quality ─────────────────────────────────────────────────────

    @Nested
    class Quality {

        @Test
        void customQuality() throws IOException {
            byte[] original = createTestImage("jpeg", 100, 80);
            byte[] stripped = stripper.strip(original, 0.5f);

            assertNotNull(stripped);
            assertTrue(stripped.length > 0);
        }

        @Test
        void highQualityProducesLargerOutput() throws IOException {
            byte[] original = createTestImage("jpeg", 200, 200);
            byte[] low = stripper.strip(original, 0.1f);
            byte[] high = stripper.strip(original, 0.95f);

            assertTrue(high.length >= low.length);
        }

        @Test
        void minimumQuality() throws IOException {
            byte[] original = createTestImage("jpeg", 100, 100);
            byte[] stripped = stripper.strip(original, 0.0f);

            assertNotNull(stripped);
            assertTrue(stripped.length > 0);
        }

        @Test
        void maximumQuality() throws IOException {
            byte[] original = createTestImage("jpeg", 100, 100);
            byte[] stripped = stripper.strip(original, 1.0f);

            assertNotNull(stripped);
            assertTrue(stripped.length > 0);
        }
    }

    // ── Format preservation ─────────────────────────────────────────

    @Nested
    class FormatPreservation {

        @Test
        void preservesPngFormat() throws IOException {
            byte[] original = createTestImage("png", 50, 50);
            byte[] stripped = stripper.strip(original);

            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals("png", meta.format().toLowerCase());
        }

        @Test
        void preservesJpegFormat() throws IOException {
            byte[] original = createTestImage("jpeg", 50, 50);
            byte[] stripped = stripper.strip(original);

            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals("jpeg", meta.format().toLowerCase());
        }

        @Test
        void preservesGifFormat() throws IOException {
            byte[] original = createTestImage("gif", 50, 50);
            byte[] stripped = stripper.strip(original);

            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals("gif", meta.format().toLowerCase());
        }
    }

    // ── Alpha channel handling ──────────────────────────────────────

    @Nested
    class AlphaChannelHandling {

        @Test
        void argbImageStrippedAsPng() throws IOException {
            byte[] original = createArgbImage("png", 80, 60);
            byte[] stripped = stripper.strip(original);

            assertNotNull(stripped);
            ImageMetadata meta = new ImageIOMetadataExtractor().extract(stripped);
            assertEquals(80, meta.width());
            assertEquals(60, meta.height());
        }

        @Test
        void argbImageStrippedAsJpeg() throws IOException {
            // JPEG does not support alpha; ImageIOUtils converts to RGB
            byte[] original = createArgbImage("png", 80, 60);
            // Read as PNG (has alpha), strip as JPEG via custom extractor
            ImageIOExifStripper jpegStripper = new ImageIOExifStripper(
                    bytes -> new ImageMetadata(80, 60, "jpeg")
            );
            byte[] stripped = jpegStripper.strip(original, 0.9f);

            assertNotNull(stripped);
            assertTrue(stripped.length > 0);
        }
    }

    // ── Custom metadata extractor ───────────────────────────────────

    @Nested
    class CustomMetadataExtractor {

        @Test
        void usesInjectedExtractor() throws IOException {
            ImageMetadataExtractor customExtractor = bytes -> new ImageMetadata(50, 50, "png");
            ImageIOExifStripper customStripper = new ImageIOExifStripper(customExtractor);

            byte[] original = createTestImage("png", 50, 50);
            byte[] stripped = customStripper.strip(original);

            assertNotNull(stripped);
            assertTrue(stripped.length > 0);
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    @Nested
    class ErrorHandling {

        @Test
        void invalidImageBytes_throws() {
            byte[] invalid = "not an image".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> stripper.strip(invalid));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void invalidImageBytesWithQuality_throws() {
            byte[] invalid = "not an image".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> stripper.strip(invalid, 0.5f));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void emptyBytes_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> stripper.strip(new byte[0]));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void randomBytes_throws() {
            byte[] random = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x10};

            assertThrows(FileStorageException.class, () -> stripper.strip(random));
        }
    }
}
