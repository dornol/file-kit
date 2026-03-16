package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImageMetadataTest {

    @Nested
    class RecordFields {

        @Test
        void accessors() {
            ImageMetadata metadata = new ImageMetadata(800, 600, "png");

            assertEquals(800, metadata.width());
            assertEquals(600, metadata.height());
            assertEquals("png", metadata.format());
        }

        @Test
        void squareImage() {
            ImageMetadata metadata = new ImageMetadata(512, 512, "jpeg");

            assertEquals(512, metadata.width());
            assertEquals(512, metadata.height());
        }

        @Test
        void singlePixel() {
            ImageMetadata metadata = new ImageMetadata(1, 1, "gif");

            assertEquals(1, metadata.width());
            assertEquals(1, metadata.height());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameValues() {
            ImageMetadata a = new ImageMetadata(100, 200, "jpeg");
            ImageMetadata b = new ImageMetadata(100, 200, "jpeg");

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality_differentWidth() {
            ImageMetadata a = new ImageMetadata(100, 200, "png");
            ImageMetadata b = new ImageMetadata(101, 200, "png");

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentHeight() {
            ImageMetadata a = new ImageMetadata(100, 200, "png");
            ImageMetadata b = new ImageMetadata(100, 201, "png");

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentFormat() {
            ImageMetadata a = new ImageMetadata(100, 200, "png");
            ImageMetadata b = new ImageMetadata(100, 200, "jpeg");

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsAllFields() {
            ImageMetadata metadata = new ImageMetadata(640, 480, "png");

            String str = metadata.toString();
            assertNotNull(str);
            assertEquals("ImageMetadata[width=640, height=480, format=png]", str);
        }
    }
}
