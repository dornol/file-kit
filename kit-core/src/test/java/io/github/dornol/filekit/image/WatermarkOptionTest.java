package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WatermarkOptionTest {

    @Nested
    class TextFactory {

        @Test
        void createsTextWatermark() {
            WatermarkOption option = WatermarkOption.text("Sample", WatermarkPosition.CENTER, 0.5f);

            assertEquals(WatermarkOption.WatermarkType.TEXT, option.type());
            assertEquals("Sample", option.text());
            assertNull(option.overlayImage());
            assertEquals(WatermarkPosition.CENTER, option.position());
            assertEquals(0.5f, option.opacity());
            assertEquals(24, option.fontSize());
            assertEquals("SansSerif", option.fontName());
            assertEquals(0.85f, option.quality());
            assertNull(option.outputFormat());
        }

        @Test
        void createsWithDifferentPositions() {
            for (WatermarkPosition pos : WatermarkPosition.values()) {
                WatermarkOption option = WatermarkOption.text("Test", pos, 0.5f);
                assertEquals(pos, option.position());
            }
        }

        @Test
        void createsWithBoundaryOpacity_zero() {
            WatermarkOption option = WatermarkOption.text("Test", WatermarkPosition.CENTER, 0.0f);
            assertEquals(0.0f, option.opacity());
        }

        @Test
        void createsWithBoundaryOpacity_one() {
            WatermarkOption option = WatermarkOption.text("Test", WatermarkPosition.CENTER, 1.0f);
            assertEquals(1.0f, option.opacity());
        }
    }

    @Nested
    class ImageFactory {

        @Test
        void createsImageWatermark() {
            byte[] overlay = new byte[]{1, 2, 3};
            WatermarkOption option = WatermarkOption.image(overlay, WatermarkPosition.BOTTOM_RIGHT, 0.3f);

            assertEquals(WatermarkOption.WatermarkType.IMAGE, option.type());
            assertNull(option.text());
            assertNotNull(option.overlayImage());
            assertEquals(3, option.overlayImage().length);
            assertEquals(WatermarkPosition.BOTTOM_RIGHT, option.position());
            assertEquals(0.3f, option.opacity());
            assertNull(option.fontName());
            assertNull(option.outputFormat());
            assertEquals(0.85f, option.quality());
        }
    }

    @Nested
    class FullConstructor {

        @Test
        void allParametersExplicit() {
            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "Test", null,
                    WatermarkPosition.TOP_LEFT, 0.7f, "Monospaced", 48, "jpeg", 0.9f);

            assertEquals(WatermarkOption.WatermarkType.TEXT, option.type());
            assertEquals("Test", option.text());
            assertNull(option.overlayImage());
            assertEquals(WatermarkPosition.TOP_LEFT, option.position());
            assertEquals(0.7f, option.opacity());
            assertEquals("Monospaced", option.fontName());
            assertEquals(48, option.fontSize());
            assertEquals("jpeg", option.outputFormat());
            assertEquals(0.9f, option.quality());
        }

        @Test
        void imageTypeWithAllParameters() {
            byte[] overlay = new byte[]{1};
            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.IMAGE, null, overlay,
                    WatermarkPosition.TILED, 0.2f, null, 1, "png", 0.5f);

            assertEquals(WatermarkOption.WatermarkType.IMAGE, option.type());
            assertNotNull(option.overlayImage());
            assertEquals(WatermarkPosition.TILED, option.position());
        }
    }

    @Nested
    class Validation {

        @Test
        void textWatermark_nullText_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WatermarkOption(WatermarkOption.WatermarkType.TEXT, null, null,
                            WatermarkPosition.CENTER, 0.5f, null, 24, null, 0.85f));
        }

        @Test
        void textWatermark_blankText_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> WatermarkOption.text("  ", WatermarkPosition.CENTER, 0.5f));
        }

        @Test
        void textWatermark_emptyText_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> WatermarkOption.text("", WatermarkPosition.CENTER, 0.5f));
        }

        @Test
        void imageWatermark_nullOverlay_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WatermarkOption(WatermarkOption.WatermarkType.IMAGE, null, null,
                            WatermarkPosition.CENTER, 0.5f, null, 24, null, 0.85f));
        }

        @Test
        void imageWatermark_emptyOverlay_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> WatermarkOption.image(new byte[0], WatermarkPosition.CENTER, 0.5f));
        }

        @Test
        void opacity_negative_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> WatermarkOption.text("test", WatermarkPosition.CENTER, -0.1f));
        }

        @Test
        void opacity_aboveOne_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> WatermarkOption.text("test", WatermarkPosition.CENTER, 1.1f));
        }

        @Test
        void fontSize_zero_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WatermarkOption(WatermarkOption.WatermarkType.TEXT, "test", null,
                            WatermarkPosition.CENTER, 0.5f, null, 0, null, 0.85f));
        }

        @Test
        void fontSize_negative_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WatermarkOption(WatermarkOption.WatermarkType.TEXT, "test", null,
                            WatermarkPosition.CENTER, 0.5f, null, -1, null, 0.85f));
        }

        @Test
        void fontSize_one_allowed() {
            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "test", null,
                    WatermarkPosition.CENTER, 0.5f, null, 1, null, 0.85f);
            assertEquals(1, option.fontSize());
        }

        @Test
        void quality_negative_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WatermarkOption(WatermarkOption.WatermarkType.TEXT, "test", null,
                            WatermarkPosition.CENTER, 0.5f, null, 24, null, -0.1f));
        }

        @Test
        void quality_aboveOne_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WatermarkOption(WatermarkOption.WatermarkType.TEXT, "test", null,
                            WatermarkPosition.CENTER, 0.5f, null, 24, null, 1.1f));
        }

        @Test
        void quality_zero_allowed() {
            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "test", null,
                    WatermarkPosition.CENTER, 0.5f, null, 24, null, 0.0f);
            assertEquals(0.0f, option.quality());
        }

        @Test
        void quality_one_allowed() {
            WatermarkOption option = new WatermarkOption(
                    WatermarkOption.WatermarkType.TEXT, "test", null,
                    WatermarkPosition.CENTER, 0.5f, null, 24, null, 1.0f);
            assertEquals(1.0f, option.quality());
        }

        @Test
        void nullType_throws() {
            assertThrows(NullPointerException.class,
                    () -> new WatermarkOption(null, "test", null,
                            WatermarkPosition.CENTER, 0.5f, null, 24, null, 0.85f));
        }

        @Test
        void nullPosition_throws() {
            assertThrows(NullPointerException.class,
                    () -> new WatermarkOption(WatermarkOption.WatermarkType.TEXT, "test", null,
                            null, 0.5f, null, 24, null, 0.85f));
        }
    }
}
