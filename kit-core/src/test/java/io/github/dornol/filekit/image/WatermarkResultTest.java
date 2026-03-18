package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkResultTest {

    @Nested
    class RecordFields {

        @Test
        void accessors() {
            byte[] data = {1, 2, 3};
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            WatermarkResult result = new WatermarkResult(data, metadata);

            assertSame(data, result.data());
            assertEquals(metadata, result.metadata());
        }

        @Test
        void dataContent() {
            byte[] data = "watermarked image".getBytes();
            ImageMetadata metadata = new ImageMetadata(800, 600, "jpeg");

            WatermarkResult result = new WatermarkResult(data, metadata);

            assertArrayEquals("watermarked image".getBytes(), result.data());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameReference() {
            byte[] data = {1, 2, 3};
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            WatermarkResult a = new WatermarkResult(data, metadata);
            WatermarkResult b = new WatermarkResult(data, metadata);

            assertEquals(a, b);
        }

        @Test
        void inequality_differentDataArrays() {
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            WatermarkResult a = new WatermarkResult(new byte[]{1, 2, 3}, metadata);
            WatermarkResult b = new WatermarkResult(new byte[]{1, 2, 3}, metadata);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentMetadata() {
            byte[] data = {1, 2, 3};

            WatermarkResult a = new WatermarkResult(data, new ImageMetadata(100, 200, "png"));
            WatermarkResult b = new WatermarkResult(data, new ImageMetadata(100, 200, "jpeg"));

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsClassName() {
            WatermarkResult result = new WatermarkResult(new byte[]{1}, new ImageMetadata(10, 20, "png"));

            String str = result.toString();
            assertNotNull(str);
            assertTrue(str.startsWith("WatermarkResult["));
        }
    }
}
