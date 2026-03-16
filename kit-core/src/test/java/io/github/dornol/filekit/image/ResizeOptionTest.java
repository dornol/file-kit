package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResizeOptionTest {

    @Nested
    class FactoryMethods {

        @Test
        void fit_createsCorrectOption() {
            ResizeOption option = ResizeOption.fit(200, 150);

            assertEquals(200, option.targetWidth());
            assertEquals(150, option.targetHeight());
            assertEquals(ScaleMode.FIT, option.scaleMode());
            assertNull(option.outputFormat());
            assertEquals(0.85f, option.quality(), 0.001f);
        }

        @Test
        void cover_createsCorrectOption() {
            ResizeOption option = ResizeOption.cover(300, 200);

            assertEquals(300, option.targetWidth());
            assertEquals(200, option.targetHeight());
            assertEquals(ScaleMode.COVER, option.scaleMode());
            assertNull(option.outputFormat());
            assertEquals(0.85f, option.quality(), 0.001f);
        }

        @Test
        void exact_createsCorrectOption() {
            ResizeOption option = ResizeOption.exact(400, 300);

            assertEquals(400, option.targetWidth());
            assertEquals(300, option.targetHeight());
            assertEquals(ScaleMode.EXACT, option.scaleMode());
            assertNull(option.outputFormat());
            assertEquals(0.85f, option.quality(), 0.001f);
        }

        @Test
        void thumbnail_createsFitWithEqualDimensions() {
            ResizeOption option = ResizeOption.thumbnail(128);

            assertEquals(128, option.targetWidth());
            assertEquals(128, option.targetHeight());
            assertEquals(ScaleMode.FIT, option.scaleMode());
            assertNull(option.outputFormat());
            assertEquals(0.85f, option.quality(), 0.001f);
        }

        @Test
        void thumbnail_equivalentToFitWithSameDimensions() {
            ResizeOption thumbnail = ResizeOption.thumbnail(256);
            ResizeOption fit = ResizeOption.fit(256, 256);

            assertEquals(thumbnail, fit);
        }
    }

    @Nested
    class DirectConstruction {

        @Test
        void withOutputFormatAndQuality() {
            ResizeOption option = new ResizeOption(500, 400, ScaleMode.EXACT, "jpeg", 0.9f);

            assertEquals(500, option.targetWidth());
            assertEquals(400, option.targetHeight());
            assertEquals(ScaleMode.EXACT, option.scaleMode());
            assertEquals("jpeg", option.outputFormat());
            assertEquals(0.9f, option.quality(), 0.001f);
        }

        @Test
        void withNullOutputFormat_keepsOriginal() {
            ResizeOption option = new ResizeOption(100, 100, ScaleMode.FIT, null, 0.5f);

            assertNull(option.outputFormat());
        }

        @Test
        void withMinimalQuality() {
            ResizeOption option = new ResizeOption(100, 100, ScaleMode.FIT, "jpeg", 0.0f);

            assertEquals(0.0f, option.quality(), 0.001f);
        }

        @Test
        void withMaxQuality() {
            ResizeOption option = new ResizeOption(100, 100, ScaleMode.FIT, "jpeg", 1.0f);

            assertEquals(1.0f, option.quality(), 0.001f);
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameValues() {
            ResizeOption a = ResizeOption.fit(200, 150);
            ResizeOption b = ResizeOption.fit(200, 150);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality_differentDimensions() {
            ResizeOption a = ResizeOption.fit(200, 150);
            ResizeOption b = ResizeOption.fit(300, 150);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentScaleMode() {
            ResizeOption a = new ResizeOption(200, 200, ScaleMode.FIT, null, 0.85f);
            ResizeOption b = new ResizeOption(200, 200, ScaleMode.COVER, null, 0.85f);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentOutputFormat() {
            ResizeOption a = new ResizeOption(200, 200, ScaleMode.FIT, null, 0.85f);
            ResizeOption b = new ResizeOption(200, 200, ScaleMode.FIT, "jpeg", 0.85f);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentQuality() {
            ResizeOption a = new ResizeOption(200, 200, ScaleMode.FIT, "jpeg", 0.5f);
            ResizeOption b = new ResizeOption(200, 200, ScaleMode.FIT, "jpeg", 0.9f);

            assertNotEquals(a, b);
        }
    }
}
