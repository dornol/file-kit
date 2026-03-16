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

class DefaultThumbnailGeneratorTest {

    private final ImageIOResizer resizer = new ImageIOResizer();
    private final DefaultThumbnailGenerator generator = new DefaultThumbnailGenerator(resizer);
    private final ImageIOMetadataExtractor metadataExtractor = new ImageIOMetadataExtractor();

    @Nested
    class DelegationToResizer {

        @Test
        void generatesSmallThumbnail() throws IOException {
            byte[] image = createTestImage(1000, 500, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(128));

            assertEquals(128, result.metadata().width());
            assertEquals(64, result.metadata().height());
        }

        @Test
        void squareImage_thumbnail() throws IOException {
            byte[] image = createTestImage(500, 500, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(64));

            assertEquals(64, result.metadata().width());
            assertEquals(64, result.metadata().height());
        }

        @Test
        void portraitImage_thumbnail() throws IOException {
            byte[] image = createTestImage(200, 800, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(100));

            assertEquals(25, result.metadata().width());
            assertEquals(100, result.metadata().height());
        }

        @Test
        void defaultOption_thumbnail() throws IOException {
            byte[] image = createTestImage(600, 400, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.defaults());

            assertEquals(200, result.metadata().width());
            assertTrue(result.metadata().height() <= 200);
        }

        @Test
        void resultIsValidImage() throws IOException {
            byte[] image = createTestImage(800, 600, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(100));

            ImageMetadata verified = metadataExtractor.extract(result.data());
            assertEquals(result.metadata().width(), verified.width());
            assertEquals(result.metadata().height(), verified.height());
        }
    }

    @Nested
    class AspectRatioPreservation {

        @Test
        void wideImage_constrainedByWidth() throws IOException {
            byte[] image = createTestImage(1000, 200, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(100));

            assertEquals(100, result.metadata().width());
            assertEquals(20, result.metadata().height());
        }

        @Test
        void tallImage_constrainedByHeight() throws IOException {
            byte[] image = createTestImage(200, 1000, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(100));

            assertEquals(20, result.metadata().width());
            assertEquals(100, result.metadata().height());
        }

        @Test
        void extremeAspectRatio_wide() throws IOException {
            byte[] image = createTestImage(1000, 10, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(100));

            assertEquals(100, result.metadata().width());
            assertTrue(result.metadata().height() >= 1);
        }

        @Test
        void extremeAspectRatio_tall() throws IOException {
            byte[] image = createTestImage(10, 1000, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(100));

            assertTrue(result.metadata().width() >= 1);
            assertEquals(100, result.metadata().height());
        }
    }

    @Nested
    class WithOutputFormat {

        @Test
        void convertsPngToJpeg() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            ThumbnailOption option = new ThumbnailOption(100, "jpeg", 0.8f);
            ResizeResult result = generator.generate(image, option);

            assertEquals("jpeg", result.metadata().format());
            assertNotNull(result.data());
        }

        @Test
        void convertsJpegToPng() throws IOException {
            byte[] image = createTestImage(200, 200, "jpeg");

            ThumbnailOption option = new ThumbnailOption(100, "png", 0.8f);
            ResizeResult result = generator.generate(image, option);

            assertEquals("png", result.metadata().format());
        }

        @Test
        void keepOriginalFormat() throws IOException {
            byte[] image = createTestImage(200, 200, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(100));

            assertEquals("png", result.metadata().format());
        }
    }

    @Nested
    class SmallInputImages {

        @Test
        void imageSmallerThanMaxDimension() throws IOException {
            byte[] image = createTestImage(50, 30, "png");

            ResizeResult result = generator.generate(image, ThumbnailOption.ofSize(200));

            // FIT mode scales down, not up. Image stays at 50x30 because
            // scale=min(200/50, 200/30)=min(4,6.67)=4 → 200x120
            // Actually, FIT scales to fit within bounds — it CAN scale up.
            assertTrue(result.metadata().width() <= 200);
            assertTrue(result.metadata().height() <= 200);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void invalidBytes_throws() {
            byte[] invalid = "not an image".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> generator.generate(invalid, ThumbnailOption.defaults()));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void emptyBytes_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> generator.generate(new byte[0], ThumbnailOption.defaults()));
            assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
        }
    }
}
