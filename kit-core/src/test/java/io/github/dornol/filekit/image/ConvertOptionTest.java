package io.github.dornol.filekit.image;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConvertOptionTest {

    @Nested
    class Validation {

        @Test
        void validOption() {
            ConvertOption option = new ConvertOption("png", 0.9f);
            assertEquals("png", option.outputFormat());
            assertEquals(0.9f, option.quality());
        }

        @Test
        void nullFormat_throws() {
            assertThrows(NullPointerException.class,
                    () -> new ConvertOption(null, 0.9f));
        }

        @Test
        void qualityTooLow_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConvertOption("png", -0.1f));
        }

        @Test
        void qualityTooHigh_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConvertOption("png", 1.1f));
        }

        @Test
        void boundaryQualityZero_valid() {
            ConvertOption option = new ConvertOption("png", 0.0f);
            assertEquals(0.0f, option.quality(), 0.001f);
        }

        @Test
        void boundaryQualityOne_valid() {
            ConvertOption option = new ConvertOption("png", 1.0f);
            assertEquals(1.0f, option.quality(), 0.001f);
        }

        @Test
        void emptyFormat_allowed() {
            // empty string is technically valid (will fail at ImageIO level)
            ConvertOption option = new ConvertOption("", 0.5f);
            assertEquals("", option.outputFormat());
        }
    }

    @Nested
    class FactoryMethods {

        @Test
        void ofFormat_defaultQuality() {
            ConvertOption option = ConvertOption.of("jpeg");
            assertEquals("jpeg", option.outputFormat());
            assertEquals(0.85f, option.quality(), 0.001f);
        }

        @Test
        void ofFormatAndQuality() {
            ConvertOption option = ConvertOption.of("png", 0.7f);
            assertEquals("png", option.outputFormat());
            assertEquals(0.7f, option.quality(), 0.001f);
        }

        @Test
        void ofFormat_nullFormat_throws() {
            assertThrows(NullPointerException.class,
                    () -> ConvertOption.of(null));
        }

        @Test
        void ofFormatAndQuality_nullFormat_throws() {
            assertThrows(NullPointerException.class,
                    () -> ConvertOption.of(null, 0.5f));
        }

        @Test
        void ofFormatAndQuality_invalidQuality_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> ConvertOption.of("png", 1.5f));
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameValues() {
            ConvertOption a = new ConvertOption("jpeg", 0.9f);
            ConvertOption b = new ConvertOption("jpeg", 0.9f);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality_differentFormat() {
            ConvertOption a = new ConvertOption("jpeg", 0.9f);
            ConvertOption b = new ConvertOption("png", 0.9f);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentQuality() {
            ConvertOption a = new ConvertOption("jpeg", 0.5f);
            ConvertOption b = new ConvertOption("jpeg", 0.9f);

            assertNotEquals(a, b);
        }

        @Test
        void factoryEqualsConstructor() {
            ConvertOption factory = ConvertOption.of("jpeg", 0.85f);
            ConvertOption direct = new ConvertOption("jpeg", 0.85f);

            assertEquals(factory, direct);
        }

        @Test
        void toString_containsAllFields() {
            ConvertOption option = new ConvertOption("png", 0.8f);
            String str = option.toString();
            assertNotNull(str);
            assertTrue(str.contains("png"));
            assertTrue(str.contains("0.8"));
        }

        private void assertTrue(boolean condition) {
            org.junit.jupiter.api.Assertions.assertTrue(condition);
        }
    }
}
