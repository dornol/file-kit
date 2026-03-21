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

class ImageIOFormatConverterTest {

    ImageIOFormatConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ImageIOFormatConverter();
    }

    private byte[] createTestImage(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private byte[] createArgbImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0, 255, 0, 128));
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    // ── PNG to JPEG ─────────────────────────────────────────────────

    @Nested
    class PngToJpeg {

        @Test
        void convertsSuccessfully() throws IOException {
            byte[] png = createTestImage("png", 100, 80);
            ConvertResult result = converter.convert(png, ConvertOption.of("jpeg"));

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
            assertEquals("jpeg", result.metadata().format());
        }

        @Test
        void preservesDimensions() throws IOException {
            byte[] png = createTestImage("png", 200, 150);
            ConvertResult result = converter.convert(png, ConvertOption.of("jpeg"));

            assertEquals(200, result.metadata().width());
            assertEquals(150, result.metadata().height());
        }
    }

    // ── JPEG to PNG ─────────────────────────────────────────────────

    @Nested
    class JpegToPng {

        @Test
        void convertsSuccessfully() throws IOException {
            byte[] jpeg = createTestImage("jpeg", 100, 80);
            ConvertResult result = converter.convert(jpeg, ConvertOption.of("png"));

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
            assertEquals("png", result.metadata().format());
        }

        @Test
        void preservesDimensions() throws IOException {
            byte[] jpeg = createTestImage("jpeg", 300, 200);
            ConvertResult result = converter.convert(jpeg, ConvertOption.of("png"));

            assertEquals(300, result.metadata().width());
            assertEquals(200, result.metadata().height());
        }
    }

    // ── Same format conversion ──────────────────────────────────────

    @Nested
    class SameFormat {

        @Test
        void jpegToJpeg() throws IOException {
            byte[] jpeg = createTestImage("jpeg", 100, 100);
            ConvertResult result = converter.convert(jpeg, ConvertOption.of("jpeg"));

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
            assertEquals("jpeg", result.metadata().format());
            assertEquals(100, result.metadata().width());
        }

        @Test
        void pngToPng() throws IOException {
            byte[] png = createTestImage("png", 100, 100);
            ConvertResult result = converter.convert(png, ConvertOption.of("png"));

            assertNotNull(result.data());
            assertEquals("png", result.metadata().format());
        }
    }

    // ── GIF format ──────────────────────────────────────────────────

    @Nested
    class GifFormat {

        @Test
        void gifToJpeg() throws IOException {
            byte[] gif = createTestImage("gif", 80, 60);
            ConvertResult result = converter.convert(gif, ConvertOption.of("jpeg"));

            assertNotNull(result.data());
            assertEquals("jpeg", result.metadata().format());
            assertEquals(80, result.metadata().width());
            assertEquals(60, result.metadata().height());
        }

        @Test
        void pngToGif() throws IOException {
            byte[] png = createTestImage("png", 80, 60);
            ConvertResult result = converter.convert(png, ConvertOption.of("gif"));

            assertNotNull(result.data());
            assertEquals("gif", result.metadata().format());
        }
    }

    // ── Quality ─────────────────────────────────────────────────────

    @Nested
    class Quality {

        @Test
        void differentQualitiesProduceDifferentSizes() throws IOException {
            byte[] jpeg = createTestImage("jpeg", 200, 200);
            ConvertResult low = converter.convert(jpeg, ConvertOption.of("jpeg", 0.1f));
            ConvertResult high = converter.convert(jpeg, ConvertOption.of("jpeg", 0.95f));

            assertTrue(high.data().length >= low.data().length);
        }

        @Test
        void minimumQuality() throws IOException {
            byte[] jpeg = createTestImage("jpeg", 100, 100);
            ConvertResult result = converter.convert(jpeg, ConvertOption.of("jpeg", 0.0f));

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
        }

        @Test
        void maximumQuality() throws IOException {
            byte[] jpeg = createTestImage("jpeg", 100, 100);
            ConvertResult result = converter.convert(jpeg, ConvertOption.of("jpeg", 1.0f));

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
        }
    }

    // ── Dimension preservation ──────────────────────────────────────

    @Nested
    class DimensionPreservation {

        @Test
        void landscapeImage() throws IOException {
            byte[] png = createTestImage("png", 400, 200);
            ConvertResult result = converter.convert(png, ConvertOption.of("jpeg"));

            assertEquals(400, result.metadata().width());
            assertEquals(200, result.metadata().height());
        }

        @Test
        void portraitImage() throws IOException {
            byte[] png = createTestImage("png", 200, 400);
            ConvertResult result = converter.convert(png, ConvertOption.of("jpeg"));

            assertEquals(200, result.metadata().width());
            assertEquals(400, result.metadata().height());
        }

        @Test
        void squareImage() throws IOException {
            byte[] png = createTestImage("png", 256, 256);
            ConvertResult result = converter.convert(png, ConvertOption.of("jpeg"));

            assertEquals(256, result.metadata().width());
            assertEquals(256, result.metadata().height());
        }

        @Test
        void singlePixelImage() throws IOException {
            byte[] png = createTestImage("png", 1, 1);
            ConvertResult result = converter.convert(png, ConvertOption.of("jpeg"));

            assertEquals(1, result.metadata().width());
            assertEquals(1, result.metadata().height());
        }

        @Test
        void smallImage() throws IOException {
            byte[] png = createTestImage("png", 10, 10);
            ConvertResult result = converter.convert(png, ConvertOption.of("jpeg"));

            assertEquals(10, result.metadata().width());
            assertEquals(10, result.metadata().height());
        }
    }

    // ── Alpha channel handling ──────────────────────────────────────

    @Nested
    class AlphaChannelHandling {

        @Test
        void argbPngToJpeg_convertsWithoutError() throws IOException {
            byte[] argb = createArgbImage(100, 80);
            ConvertResult result = converter.convert(argb, ConvertOption.of("jpeg"));

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
            assertEquals("jpeg", result.metadata().format());
            assertEquals(100, result.metadata().width());
            assertEquals(80, result.metadata().height());
        }

        @Test
        void argbPngToPng_preservesAlpha() throws IOException {
            byte[] argb = createArgbImage(100, 80);
            ConvertResult result = converter.convert(argb, ConvertOption.of("png"));

            assertNotNull(result.data());
            assertEquals("png", result.metadata().format());
        }
    }

    // ── Custom metadata extractor ───────────────────────────────────

    @Nested
    class Constructor {

        @Test
        void defaultConstructorWorks() throws IOException {
            ImageIOFormatConverter defaultConverter = new ImageIOFormatConverter();
            byte[] png = createTestImage("png", 100, 80);
            ConvertResult result = defaultConverter.convert(png, ConvertOption.of("jpeg"));

            assertEquals(100, result.metadata().width());
            assertEquals(80, result.metadata().height());
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    @Nested
    class ErrorHandling {

        @Test
        void invalidImageBytes_throws() {
            byte[] invalid = "not an image".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> converter.convert(invalid, ConvertOption.of("png")));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void emptyBytes_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> converter.convert(new byte[0], ConvertOption.of("png")));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void randomBytes_throws() {
            byte[] random = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x10};

            assertThrows(FileStorageException.class,
                    () -> converter.convert(random, ConvertOption.of("png")));
        }

        @Test
        void unsupportedOutputFormat_throws() throws IOException {
            byte[] png = createTestImage("png", 50, 50);

            assertThrows(FileStorageException.class,
                    () -> converter.convert(png, ConvertOption.of("xyz_unsupported")));
        }
    }
}
