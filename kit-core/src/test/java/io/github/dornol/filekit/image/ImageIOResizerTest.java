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

class ImageIOResizerTest {

    private final ImageIOResizer resizer = new ImageIOResizer();
    private final ImageIOMetadataExtractor metadataExtractor = new ImageIOMetadataExtractor();

    @Nested
    class FitMode {

        @Test
        void landscapeImage_scalesDownPreservingAspectRatio() throws IOException {
            byte[] image = createTestImage(800, 600, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.fit(400, 400));

            assertEquals(400, result.metadata().width());
            assertEquals(300, result.metadata().height());
            assertEquals("png", result.metadata().format());
            assertNotNull(result.data());
            assertTrue(result.data().length > 0);
        }

        @Test
        void portraitImage_scalesDownPreservingAspectRatio() throws IOException {
            byte[] image = createTestImage(200, 800, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.fit(200, 400));

            assertEquals(100, result.metadata().width());
            assertEquals(400, result.metadata().height());
        }

        @Test
        void squareImage_fitsIntoRectangularTarget() throws IOException {
            byte[] image = createTestImage(500, 500, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.fit(200, 100));

            assertEquals(100, result.metadata().width());
            assertEquals(100, result.metadata().height());
        }

        @Test
        void wideImage_constrainedByWidth() throws IOException {
            byte[] image = createTestImage(1000, 200, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.fit(500, 500));

            assertEquals(500, result.metadata().width());
            assertEquals(100, result.metadata().height());
        }

        @Test
        void thumbnail_generatesSmallImage() throws IOException {
            byte[] image = createTestImage(1000, 500, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.thumbnail(128));

            assertEquals(128, result.metadata().width());
            assertEquals(64, result.metadata().height());
        }

        @Test
        void thumbnail_squareInput() throws IOException {
            byte[] image = createTestImage(500, 500, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.thumbnail(64));

            assertEquals(64, result.metadata().width());
            assertEquals(64, result.metadata().height());
        }

        @Test
        void resultIsValidImage() throws IOException {
            byte[] image = createTestImage(600, 400, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.fit(300, 300));

            // Verify result bytes are a valid image by re-extracting metadata
            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals(result.metadata().width(), verified.width());
            assertEquals(result.metadata().height(), verified.height());
        }
    }

    @Nested
    class CoverMode {

        @Test
        void landscapeImage_coversSquareTarget() throws IOException {
            byte[] image = createTestImage(800, 600, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.cover(200, 200));

            assertEquals(200, result.metadata().width());
            assertEquals(200, result.metadata().height());
        }

        @Test
        void portraitImage_coversSquareTarget() throws IOException {
            byte[] image = createTestImage(400, 800, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.cover(200, 200));

            assertEquals(200, result.metadata().width());
            assertEquals(200, result.metadata().height());
        }

        @Test
        void wideImage_coversTarget() throws IOException {
            byte[] image = createTestImage(1000, 400, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.cover(300, 300));

            assertEquals(300, result.metadata().width());
            assertEquals(300, result.metadata().height());
        }

        @Test
        void squareImage_coversRectangularTarget() throws IOException {
            byte[] image = createTestImage(500, 500, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.cover(300, 200));

            assertEquals(300, result.metadata().width());
            assertEquals(200, result.metadata().height());
        }

        @Test
        void resultIsValidImage() throws IOException {
            byte[] image = createTestImage(600, 400, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.cover(150, 150));

            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals(150, verified.width());
            assertEquals(150, verified.height());
        }
    }

    @Nested
    class ExactMode {

        @Test
        void stretchesToExactDimensions() throws IOException {
            byte[] image = createTestImage(800, 600, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.exact(200, 100));

            assertEquals(200, result.metadata().width());
            assertEquals(100, result.metadata().height());
        }

        @Test
        void stretchWideToTall() throws IOException {
            byte[] image = createTestImage(1000, 200, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.exact(100, 500));

            assertEquals(100, result.metadata().width());
            assertEquals(500, result.metadata().height());
        }

        @Test
        void stretchToSquare() throws IOException {
            byte[] image = createTestImage(800, 400, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.exact(300, 300));

            assertEquals(300, result.metadata().width());
            assertEquals(300, result.metadata().height());
        }

        @Test
        void resultIsValidImage() throws IOException {
            byte[] image = createTestImage(400, 300, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.exact(250, 175));

            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals(250, verified.width());
            assertEquals(175, verified.height());
        }
    }

    @Nested
    class FormatConversion {

        @Test
        void pngToJpeg() throws IOException {
            byte[] image = createTestImage(100, 100, "png");

            ResizeOption option = new ResizeOption(50, 50, ScaleMode.FIT, "jpeg", 0.85f);
            ResizeResult result = resizer.resize(image, option);

            assertEquals("jpeg", result.metadata().format());
            assertTrue(result.data().length > 0);
            // Verify the output is indeed a valid JPEG
            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals("jpeg", verified.format());
        }

        @Test
        void jpegToPng() throws IOException {
            byte[] image = createTestImage(100, 100, "jpeg");

            ResizeOption option = new ResizeOption(80, 80, ScaleMode.FIT, "png", 0.85f);
            ResizeResult result = resizer.resize(image, option);

            assertEquals("png", result.metadata().format());
            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals("png", verified.format());
        }

        @Test
        void keepOriginalFormat_whenOutputFormatNull() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            ResizeResult result = resizer.resize(image, ResizeOption.fit(100, 100));

            assertEquals("png", result.metadata().format());
        }
    }

    @Nested
    class Quality {

        @Test
        void lowQuality_producesSmallerOutput() throws IOException {
            byte[] image = createTestImage(500, 500, "jpeg");

            ResizeOption highQ = new ResizeOption(200, 200, ScaleMode.FIT, "jpeg", 0.95f);
            ResizeOption lowQ = new ResizeOption(200, 200, ScaleMode.FIT, "jpeg", 0.1f);

            ResizeResult highResult = resizer.resize(image, highQ);
            ResizeResult lowResult = resizer.resize(image, lowQ);

            assertTrue(lowResult.data().length < highResult.data().length,
                    "Low quality (%d bytes) should produce smaller output than high quality (%d bytes)"
                            .formatted(lowResult.data().length, highResult.data().length));
        }

        @Test
        void midQuality_betweenHighAndLow() throws IOException {
            byte[] image = createTestImage(300, 300, "jpeg");

            ResizeOption highQ = new ResizeOption(150, 150, ScaleMode.FIT, "jpeg", 0.95f);
            ResizeOption midQ = new ResizeOption(150, 150, ScaleMode.FIT, "jpeg", 0.5f);
            ResizeOption lowQ = new ResizeOption(150, 150, ScaleMode.FIT, "jpeg", 0.1f);

            ResizeResult highResult = resizer.resize(image, highQ);
            ResizeResult midResult = resizer.resize(image, midQ);
            ResizeResult lowResult = resizer.resize(image, lowQ);

            assertTrue(lowResult.data().length <= midResult.data().length,
                    "Low quality should be <= mid quality");
            assertTrue(midResult.data().length <= highResult.data().length,
                    "Mid quality should be <= high quality");
        }
    }

    @Nested
    class JpegAlphaHandling {

        @Test
        void argbImageToJpeg_handledCorrectly() throws IOException {
            // Create ARGB image (has alpha channel)
            byte[] image = ImageIOMetadataExtractorTest.writeImage(
                    new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                    "png"
            );

            ResizeOption option = new ResizeOption(50, 50, ScaleMode.FIT, "jpeg", 0.85f);
            ResizeResult result = resizer.resize(image, option);

            assertEquals("jpeg", result.metadata().format());
            // JPEG should be writable without error (alpha removed)
            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals(50, verified.width());
            assertEquals(50, verified.height());
        }
    }

    @Nested
    class CustomMetadataExtractor {

        @Test
        void usesProvidedExtractor() throws IOException {
            ImageIOMetadataExtractor customExtractor = new ImageIOMetadataExtractor();
            ImageIOResizer customResizer = new ImageIOResizer(customExtractor);

            byte[] image = createTestImage(200, 200, "png");
            ResizeResult result = customResizer.resize(image, ResizeOption.fit(100, 100));

            assertEquals(100, result.metadata().width());
            assertEquals(100, result.metadata().height());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void invalidBytes_throws() {
            byte[] invalid = "not an image".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> resizer.resize(invalid, ResizeOption.fit(100, 100)));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void emptyBytes_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> resizer.resize(new byte[0], ResizeOption.fit(100, 100)));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void invalidBytes_withCoverMode_throws() {
            byte[] invalid = "garbage".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> resizer.resize(invalid, ResizeOption.cover(100, 100)));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void invalidBytes_withExactMode_throws() {
            byte[] invalid = "garbage".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> resizer.resize(invalid, ResizeOption.exact(100, 100)));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }
    }
}
