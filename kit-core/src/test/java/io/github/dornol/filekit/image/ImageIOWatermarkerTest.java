package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.github.dornol.filekit.image.ImageIOMetadataExtractorTest.createTestImage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageIOWatermarkerTest {

    private final ImageIOWatermarker watermarker = new ImageIOWatermarker();
    private final ImageIOMetadataExtractor metadataExtractor = new ImageIOMetadataExtractor();

    @Nested
    class TextWatermark {

        @Test
        void center_appliesTextWatermark() throws IOException {
            byte[] image = createTestImage(400, 300, "png");

            WatermarkOption option = WatermarkOption.text("Sample", WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
            assertEquals(400, result.metadata().width());
            assertEquals(300, result.metadata().height());
            assertEquals("png", result.metadata().format());
        }

        @Test
        void topLeft_appliesTextWatermark() throws IOException {
            byte[] image = createTestImage(300, 200, "png");

            WatermarkOption option = WatermarkOption.text("TL", WatermarkPosition.TOP_LEFT, 0.7f);
            WatermarkResult result = watermarker.apply(image, option);

            assertEquals(300, result.metadata().width());
            assertEquals(200, result.metadata().height());
        }

        @Test
        void bottomRight_appliesTextWatermark() throws IOException {
            byte[] image = createTestImage(300, 200, "png");

            WatermarkOption option = WatermarkOption.text("BR", WatermarkPosition.BOTTOM_RIGHT, 0.3f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
        }

        @Test
        void topRight_appliesTextWatermark() throws IOException {
            byte[] image = createTestImage(300, 200, "png");

            WatermarkOption option = WatermarkOption.text("TR", WatermarkPosition.TOP_RIGHT, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void bottomLeft_appliesTextWatermark() throws IOException {
            byte[] image = createTestImage(300, 200, "png");

            WatermarkOption option = WatermarkOption.text("BL", WatermarkPosition.BOTTOM_LEFT, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void resultIsValidImage() throws IOException {
            byte[] image = createTestImage(500, 400, "png");

            WatermarkOption option = WatermarkOption.text("Watermark", WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals(500, verified.width());
            assertEquals(400, verified.height());
        }

        @Test
        void preservesDimensions_allPositions() throws IOException {
            byte[] image = createTestImage(300, 200, "png");

            for (WatermarkPosition pos : WatermarkPosition.values()) {
                WatermarkResult result = watermarker.apply(image,
                        WatermarkOption.text("Test", pos, 0.5f));
                assertEquals(300, result.metadata().width(),
                        "Width mismatch for position " + pos);
                assertEquals(200, result.metadata().height(),
                        "Height mismatch for position " + pos);
            }
        }

        @Test
        void withCustomFont() throws IOException {
            byte[] image = createTestImage(300, 200, "png");

            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "Custom Font", null,
                    WatermarkPosition.CENTER, 0.5f, "Serif", 36, null, 0.85f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(300, result.metadata().width());
        }

        @Test
        void withLargeFontSize() throws IOException {
            byte[] image = createTestImage(500, 500, "png");

            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "BIG", null,
                    WatermarkPosition.CENTER, 0.5f, "SansSerif", 100, null, 0.85f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void withSmallFontSize() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "tiny", null,
                    WatermarkPosition.CENTER, 0.5f, "SansSerif", 1, null, 0.85f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void longTextWatermark() throws IOException {
            byte[] image = createTestImage(400, 300, "png");
            String longText = "This is a very long watermark text that exceeds the image width";

            WatermarkOption option = WatermarkOption.text(longText, WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(400, result.metadata().width());
        }
    }

    @Nested
    class FormatConversion {

        @Test
        void pngToJpeg() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "Test", null,
                    WatermarkPosition.CENTER, 0.5f, "SansSerif", 24, "jpeg", 0.85f);
            WatermarkResult result = watermarker.apply(image, option);

            assertEquals("jpeg", result.metadata().format());
            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals("jpeg", verified.format());
        }

        @Test
        void jpegToPng() throws IOException {
            byte[] image = createTestImage(200, 200, "jpeg");

            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "Test", null,
                    WatermarkPosition.CENTER, 0.5f, "SansSerif", 24, "png", 0.85f);
            WatermarkResult result = watermarker.apply(image, option);

            assertEquals("png", result.metadata().format());
        }

        @Test
        void keepOriginalFormat_whenOutputFormatNull() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            WatermarkOption option = WatermarkOption.text("Test", WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertEquals("png", result.metadata().format());
        }
    }

    @Nested
    class ImageWatermark {

        @Test
        void center_appliesImageOverlay() throws IOException {
            byte[] image = createTestImage(400, 300, "png");
            byte[] overlay = createTestImage(50, 50, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(400, result.metadata().width());
            assertEquals(300, result.metadata().height());
        }

        @Test
        void bottomRight_appliesImageOverlay() throws IOException {
            byte[] image = createTestImage(400, 300, "png");
            byte[] overlay = createTestImage(30, 30, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.BOTTOM_RIGHT, 0.7f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void topLeft_appliesImageOverlay() throws IOException {
            byte[] image = createTestImage(400, 300, "png");
            byte[] overlay = createTestImage(30, 30, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.TOP_LEFT, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(400, result.metadata().width());
        }

        @Test
        void topRight_appliesImageOverlay() throws IOException {
            byte[] image = createTestImage(400, 300, "png");
            byte[] overlay = createTestImage(30, 30, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.TOP_RIGHT, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void bottomLeft_appliesImageOverlay() throws IOException {
            byte[] image = createTestImage(400, 300, "png");
            byte[] overlay = createTestImage(30, 30, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.BOTTOM_LEFT, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void resultIsValidImage() throws IOException {
            byte[] image = createTestImage(400, 300, "png");
            byte[] overlay = createTestImage(50, 50, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals(400, verified.width());
            assertEquals(300, verified.height());
        }

        @Test
        void overlayLargerThanImage() throws IOException {
            byte[] image = createTestImage(100, 100, "png");
            byte[] overlay = createTestImage(200, 200, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            // Should not crash; overlay extends beyond image boundaries
            assertNotNull(result.data());
            assertEquals(100, result.metadata().width());
        }
    }

    @Nested
    class TiledWatermark {

        @Test
        void tiledText_repeatsAcrossImage() throws IOException {
            byte[] image = createTestImage(500, 500, "png");

            WatermarkOption option = WatermarkOption.text("TILE", WatermarkPosition.TILED, 0.3f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(500, result.metadata().width());
            assertEquals(500, result.metadata().height());
        }

        @Test
        void tiledImage_repeatsAcrossImage() throws IOException {
            byte[] image = createTestImage(400, 400, "png");
            byte[] overlay = createTestImage(50, 50, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.TILED, 0.3f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(400, result.metadata().width());
        }

        @Test
        void tiledText_smallImage() throws IOException {
            byte[] image = createTestImage(50, 50, "png");

            WatermarkOption option = WatermarkOption.text("X", WatermarkPosition.TILED, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(50, result.metadata().width());
        }

        @Test
        void tiledImage_overlayLargerThanImage() throws IOException {
            byte[] image = createTestImage(100, 100, "png");
            byte[] overlay = createTestImage(200, 200, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.TILED, 0.3f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }
    }

    @Nested
    class Opacity {

        @Test
        void fullOpacity() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            WatermarkOption option = WatermarkOption.text("Full", WatermarkPosition.CENTER, 1.0f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
        }

        @Test
        void zeroOpacity() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            WatermarkOption option = WatermarkOption.text("None", WatermarkPosition.CENTER, 0.0f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }

        @Test
        void halfOpacity_imageWatermark() throws IOException {
            byte[] image = createTestImage(300, 300, "png");
            byte[] overlay = createTestImage(50, 50, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }
    }

    @Nested
    class SmallImages {

        @Test
        void singlePixelImage() throws IOException {
            byte[] image = createTestImage(1, 1, "png");

            WatermarkOption option = WatermarkOption.text("X", WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals(1, result.metadata().width());
            assertEquals(1, result.metadata().height());
        }

        @Test
        void verySmallImage_allPositions() throws IOException {
            byte[] image = createTestImage(10, 10, "png");

            for (WatermarkPosition pos : WatermarkPosition.values()) {
                WatermarkResult result = watermarker.apply(image,
                        WatermarkOption.text("W", pos, 0.5f));
                assertNotNull(result.data(), "Failed for position " + pos);
            }
        }
    }

    @Nested
    class CustomMetadataExtractor {

        @Test
        void usesProvidedExtractor() throws IOException {
            ImageIOMetadataExtractor customExtractor = new ImageIOMetadataExtractor();
            ImageIOWatermarker customWatermarker = new ImageIOWatermarker(customExtractor);

            byte[] image = createTestImage(200, 200, "png");
            WatermarkResult result = customWatermarker.apply(image,
                    WatermarkOption.text("Test", WatermarkPosition.CENTER, 0.5f));

            assertEquals(200, result.metadata().width());
            assertEquals(200, result.metadata().height());
        }
    }

    @Nested
    class JpegInput {

        @Test
        void appliesWatermarkToJpeg() throws IOException {
            byte[] image = createTestImage(300, 200, "jpeg");

            WatermarkOption option = WatermarkOption.text("JPEG", WatermarkPosition.CENTER, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
            assertEquals("jpeg", result.metadata().format());
        }

        @Test
        void jpegImageWatermark() throws IOException {
            byte[] image = createTestImage(300, 200, "jpeg");
            byte[] overlay = createTestImage(50, 50, "png");

            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.BOTTOM_RIGHT, 0.5f);
            WatermarkResult result = watermarker.apply(image, option);

            assertNotNull(result.data());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void invalidBytes_throws() {
            byte[] invalid = "not an image".getBytes();

            WatermarkOption option = WatermarkOption.text("Test", WatermarkPosition.CENTER, 0.5f);
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> watermarker.apply(invalid, option));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void emptyBytes_throws() {
            WatermarkOption option = WatermarkOption.text("Test", WatermarkPosition.CENTER, 0.5f);
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> watermarker.apply(new byte[0], option));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void invalidOverlayImage_throws() throws IOException {
            byte[] image = createTestImage(200, 200, "png");
            byte[] invalidOverlay = "not an image".getBytes();

            WatermarkOption option = WatermarkOption.image(invalidOverlay, WatermarkPosition.CENTER, 0.5f);
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> watermarker.apply(image, option));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void randomBytes_throws() {
            byte[] random = new byte[]{0x00, 0x01, 0x02, 0x03};

            WatermarkOption option = WatermarkOption.text("Test", WatermarkPosition.CENTER, 0.5f);
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> watermarker.apply(random, option));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void invalidOverlayImage_tiled_throws() throws IOException {
            byte[] image = createTestImage(200, 200, "png");
            byte[] invalidOverlay = "not an image".getBytes();

            WatermarkOption option = WatermarkOption.image(invalidOverlay, WatermarkPosition.TILED, 0.5f);
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> watermarker.apply(image, option));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }
    }
}
