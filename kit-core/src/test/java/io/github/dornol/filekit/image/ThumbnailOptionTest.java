package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThumbnailOptionTest {

    @Nested
    class FactoryMethods {

        @Test
        void defaults_creates200px() {
            ThumbnailOption option = ThumbnailOption.defaults();

            assertEquals(200, option.maxDimension());
            assertNull(option.outputFormat());
            assertEquals(0.8f, option.quality());
        }

        @Test
        void ofSize_createsWithDimension() {
            ThumbnailOption option = ThumbnailOption.ofSize(128);

            assertEquals(128, option.maxDimension());
            assertNull(option.outputFormat());
            assertEquals(0.8f, option.quality());
        }

        @Test
        void ofSize_minimumDimension() {
            ThumbnailOption option = ThumbnailOption.ofSize(1);
            assertEquals(1, option.maxDimension());
        }

        @Test
        void ofSize_largeDimension() {
            ThumbnailOption option = ThumbnailOption.ofSize(10000);
            assertEquals(10000, option.maxDimension());
        }
    }

    @Nested
    class FullConstructor {

        @Test
        void allParameters() {
            ThumbnailOption option = new ThumbnailOption(256, "jpeg", 0.9f);

            assertEquals(256, option.maxDimension());
            assertEquals("jpeg", option.outputFormat());
            assertEquals(0.9f, option.quality());
        }

        @Test
        void nullOutputFormat_allowed() {
            ThumbnailOption option = new ThumbnailOption(100, null, 0.5f);
            assertNull(option.outputFormat());
        }
    }

    @Nested
    class Validation {

        @Test
        void maxDimension_zero_throws() {
            assertThrows(IllegalArgumentException.class, () -> ThumbnailOption.ofSize(0));
        }

        @Test
        void maxDimension_negative_throws() {
            assertThrows(IllegalArgumentException.class, () -> ThumbnailOption.ofSize(-1));
        }

        @Test
        void maxDimension_negativeMax_throws() {
            assertThrows(IllegalArgumentException.class, () -> ThumbnailOption.ofSize(Integer.MIN_VALUE));
        }

        @Test
        void quality_negative_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ThumbnailOption(100, null, -0.1f));
        }

        @Test
        void quality_aboveOne_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ThumbnailOption(100, null, 1.1f));
        }

        @Test
        void quality_zero_allowed() {
            ThumbnailOption option = new ThumbnailOption(100, null, 0.0f);
            assertEquals(0.0f, option.quality());
        }

        @Test
        void quality_one_allowed() {
            ThumbnailOption option = new ThumbnailOption(100, null, 1.0f);
            assertEquals(1.0f, option.quality());
        }
    }
}
