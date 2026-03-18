package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConvertResultTest {

    @Nested
    class RecordFields {

        @Test
        void accessors() {
            byte[] data = {1, 2, 3};
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            ConvertResult result = new ConvertResult(data, metadata);

            assertSame(data, result.data());
            assertEquals(metadata, result.metadata());
        }

        @Test
        void dataContent() {
            byte[] data = "image bytes".getBytes();
            ImageMetadata metadata = new ImageMetadata(640, 480, "jpeg");

            ConvertResult result = new ConvertResult(data, metadata);

            assertArrayEquals("image bytes".getBytes(), result.data());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameReference() {
            byte[] data = {1, 2, 3};
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            ConvertResult a = new ConvertResult(data, metadata);
            ConvertResult b = new ConvertResult(data, metadata);

            // byte[] uses reference equality in records
            assertEquals(a, b);
        }

        @Test
        void inequality_differentDataArrays() {
            ImageMetadata metadata = new ImageMetadata(100, 200, "png");

            ConvertResult a = new ConvertResult(new byte[]{1, 2, 3}, metadata);
            ConvertResult b = new ConvertResult(new byte[]{1, 2, 3}, metadata);

            // Different byte[] instances → not equal (reference equality)
            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentMetadata() {
            byte[] data = {1, 2, 3};

            ConvertResult a = new ConvertResult(data, new ImageMetadata(100, 200, "png"));
            ConvertResult b = new ConvertResult(data, new ImageMetadata(100, 200, "jpeg"));

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsClassName() {
            ConvertResult result = new ConvertResult(new byte[]{1}, new ImageMetadata(10, 20, "png"));

            String str = result.toString();
            assertNotNull(str);
            assertTrue(str.startsWith("ConvertResult["));
        }

        private void assertTrue(boolean condition) {
            org.junit.jupiter.api.Assertions.assertTrue(condition);
        }
    }
}
