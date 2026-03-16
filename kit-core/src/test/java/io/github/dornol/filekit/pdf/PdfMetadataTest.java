package io.github.dornol.filekit.pdf;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfMetadataTest {

    @Nested
    class Construction {

        @Test
        void validMetadata() {
            Instant now = Instant.now();
            PdfMetadata meta = new PdfMetadata(5, "Title", "Author", "Creator", now);

            assertEquals(5, meta.pageCount());
            assertEquals("Title", meta.title());
            assertEquals("Author", meta.author());
            assertEquals("Creator", meta.creator());
            assertEquals(now, meta.creationDate());
        }

        @Test
        void nullableFieldsAllowed() {
            PdfMetadata meta = new PdfMetadata(1, null, null, null, null);

            assertEquals(1, meta.pageCount());
            assertNull(meta.title());
            assertNull(meta.author());
            assertNull(meta.creator());
            assertNull(meta.creationDate());
        }

        @Test
        void zeroPageCount_allowed() {
            PdfMetadata meta = new PdfMetadata(0, null, null, null, null);
            assertEquals(0, meta.pageCount());
        }

        @Test
        void emptyStringFields_allowed() {
            PdfMetadata meta = new PdfMetadata(1, "", "", "", null);
            assertEquals("", meta.title());
            assertEquals("", meta.author());
            assertEquals("", meta.creator());
        }

        @Test
        void largePageCount() {
            PdfMetadata meta = new PdfMetadata(100000, null, null, null, null);
            assertEquals(100000, meta.pageCount());
        }
    }

    @Nested
    class Validation {

        @Test
        void negativePageCount_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PdfMetadata(-1, null, null, null, null));
        }

        @Test
        void negativeMaxPageCount_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PdfMetadata(Integer.MIN_VALUE, null, null, null, null));
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equals_sameValues() {
            Instant now = Instant.now();
            PdfMetadata meta1 = new PdfMetadata(1, "Title", "Author", "Creator", now);
            PdfMetadata meta2 = new PdfMetadata(1, "Title", "Author", "Creator", now);

            assertEquals(meta1, meta2);
        }

        @Test
        void equals_differentValues() {
            PdfMetadata meta1 = new PdfMetadata(1, "A", null, null, null);
            PdfMetadata meta2 = new PdfMetadata(2, "B", null, null, null);

            assertNotEquals(meta1, meta2);
        }

        @Test
        void hashCode_consistent() {
            PdfMetadata meta1 = new PdfMetadata(1, "Title", null, null, null);
            PdfMetadata meta2 = new PdfMetadata(1, "Title", null, null, null);

            assertEquals(meta1.hashCode(), meta2.hashCode());
        }

        @Test
        void toString_containsFields() {
            PdfMetadata meta = new PdfMetadata(3, "Title", "Author", null, null);
            String str = meta.toString();

            assertEquals(true, str.contains("3"));
            assertEquals(true, str.contains("Title"));
            assertEquals(true, str.contains("Author"));
        }
    }
}
