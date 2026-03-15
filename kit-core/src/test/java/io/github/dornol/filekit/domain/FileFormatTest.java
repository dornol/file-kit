package io.github.dornol.filekit.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileFormatTest {

    @Nested
    class Construction {

        @Test
        void validConstruction() {
            FileFormat format = new FileFormat("image/jpeg", "jpg", "image");

            assertEquals("image/jpeg", format.mimeType());
            assertEquals("jpg", format.extension());
            assertEquals("image", format.primaryType());
        }

        @Test
        void validConstruction_applicationPdf() {
            FileFormat format = new FileFormat("application/pdf", "pdf", "application");

            assertEquals("application/pdf", format.mimeType());
            assertEquals("pdf", format.extension());
            assertEquals("application", format.primaryType());
        }

        @Test
        void validConstruction_emptyExtension() {
            assertDoesNotThrow(() -> new FileFormat("application/octet-stream", "", "application"));
        }

        @Test
        void validConstruction_emptyMimeType() {
            assertDoesNotThrow(() -> new FileFormat("", "bin", ""));
        }
    }

    @Nested
    class NullValidation {

        @Test
        void nullMimeType_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileFormat(null, "jpg", "image"));
            assertEquals("mimeType", ex.getMessage());
        }

        @Test
        void nullExtension_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileFormat("image/jpeg", null, "image"));
            assertEquals("extension", ex.getMessage());
        }

        @Test
        void nullPrimaryType_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileFormat("image/jpeg", "jpg", null));
            assertEquals("primaryType", ex.getMessage());
        }

        @Test
        void allNull_throwsForFirstField() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileFormat(null, null, null));
            assertEquals("mimeType", ex.getMessage());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equals_sameValues() {
            FileFormat a = new FileFormat("text/plain", "txt", "text");
            FileFormat b = new FileFormat("text/plain", "txt", "text");
            assertEquals(a, b);
        }

        @Test
        void hashCode_sameValues() {
            FileFormat a = new FileFormat("text/plain", "txt", "text");
            FileFormat b = new FileFormat("text/plain", "txt", "text");
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toString_containsValues() {
            FileFormat format = new FileFormat("image/png", "png", "image");
            String str = format.toString();
            assertEquals(true, str.contains("image/png"));
            assertEquals(true, str.contains("png"));
            assertEquals(true, str.contains("image"));
        }
    }
}
