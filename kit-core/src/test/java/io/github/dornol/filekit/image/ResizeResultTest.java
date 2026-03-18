package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResizeResultTest {

    @Nested
    class RecordFields {

        @Test
        void accessors() {
            byte[] data = {1, 2, 3};
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            ResizeResult result = new ResizeResult(data, metadata);

            assertSame(data, result.data());
            assertEquals(metadata, result.metadata());
        }

        @Test
        void dataContent() {
            byte[] data = "resized image".getBytes();
            ImageMetadata metadata = new ImageMetadata(320, 240, "jpeg");

            ResizeResult result = new ResizeResult(data, metadata);

            assertArrayEquals("resized image".getBytes(), result.data());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameReference() {
            byte[] data = {1, 2, 3};
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            ResizeResult a = new ResizeResult(data, metadata);
            ResizeResult b = new ResizeResult(data, metadata);

            assertEquals(a, b);
        }

        @Test
        void inequality_differentDataArrays() {
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            ResizeResult a = new ResizeResult(new byte[]{1, 2, 3}, metadata);
            ResizeResult b = new ResizeResult(new byte[]{1, 2, 3}, metadata);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentMetadata() {
            byte[] data = {1, 2, 3};

            ResizeResult a = new ResizeResult(data, new ImageMetadata(100, 200, "png"));
            ResizeResult b = new ResizeResult(data, new ImageMetadata(50, 100, "png"));

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsClassName() {
            ResizeResult result = new ResizeResult(new byte[]{1}, new ImageMetadata(10, 20, "png"));

            String str = result.toString();
            assertNotNull(str);
            assertTrue(str.startsWith("ResizeResult["));
        }
    }
}
