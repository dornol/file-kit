package io.github.dornol.filekit.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileMetadataTest {

    enum Storage { LOCAL }

    private final FileFormat format = new FileFormat("text/plain", "txt", "text");
    private final FileLocation location = new FileLocation("bucket", "key", Storage.LOCAL);

    @Nested
    class Construction {

        @Test
        void validConstruction() {
            FileMetadata meta = new FileMetadata("key", "file.txt", 100, "checksum", format, location);

            assertEquals("key", meta.key());
            assertEquals("file.txt", meta.name());
            assertEquals(100, meta.size());
            assertEquals("checksum", meta.checksum());
            assertEquals(format, meta.format());
            assertEquals(location, meta.location());
        }

        @Test
        void zeroSize_allowed() {
            assertDoesNotThrow(() -> new FileMetadata("key", "empty.txt", 0, "checksum", format, location));
        }

        @Test
        void largeSize_allowed() {
            assertDoesNotThrow(() -> new FileMetadata("key", "big.bin", Long.MAX_VALUE, "checksum", format, location));
        }

        @Test
        void emptyName_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FileMetadata("key", "", 0, "checksum", format, location));
        }

        @Test
        void emptyKey_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FileMetadata("", "file.txt", 0, "checksum", format, location));
        }

        @Test
        void emptyChecksum_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FileMetadata("key", "file.txt", 0, "", format, location));
        }
    }

    @Nested
    class NullValidation {

        @Test
        void nullKey_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileMetadata(null, "file.txt", 100, "checksum", format, location));
            assertEquals("key", ex.getMessage());
        }

        @Test
        void nullName_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileMetadata("key", null, 100, "checksum", format, location));
            assertEquals("name", ex.getMessage());
        }

        @Test
        void nullChecksum_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileMetadata("key", "file.txt", 100, null, format, location));
            assertEquals("checksum", ex.getMessage());
        }

        @Test
        void nullFormat_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileMetadata("key", "file.txt", 100, "checksum", null, location));
            assertEquals("format", ex.getMessage());
        }

        @Test
        void nullLocation_throws() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new FileMetadata("key", "file.txt", 100, "checksum", format, null));
            assertEquals("location", ex.getMessage());
        }
    }

    @Nested
    class SizeValidation {

        @Test
        void negativeSize_throws() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new FileMetadata("key", "file.txt", -1, "checksum", format, location));
            assertEquals("size must not be negative: -1", ex.getMessage());
        }

        @Test
        void negativeLargeSize_throws() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new FileMetadata("key", "file.txt", Long.MIN_VALUE, "checksum", format, location));
            assertEquals("size must not be negative: " + Long.MIN_VALUE, ex.getMessage());
        }
    }

    @Nested
    class WithName {

        @Test
        void returnsNewInstanceWithUpdatedName() {
            FileMetadata original = new FileMetadata("key", "old.txt", 100, "chk", format, location);
            FileMetadata renamed = original.withName("new.txt");

            assertEquals("new.txt", renamed.name());
            assertEquals("key", renamed.key());
            assertEquals(100, renamed.size());
            assertEquals("chk", renamed.checksum());
            assertEquals(format, renamed.format());
            assertEquals(location, renamed.location());
        }

        @Test
        void originalUnchanged() {
            FileMetadata original = new FileMetadata("key", "old.txt", 100, "chk", format, location);
            original.withName("new.txt");

            assertEquals("old.txt", original.name());
        }

        @Test
        void nullNewName_throws() {
            FileMetadata meta = new FileMetadata("key", "file.txt", 100, "chk", format, location);
            assertThrows(NullPointerException.class, () -> meta.withName(null));
        }

        @Test
        void sameNameProducesDifferentInstance() {
            FileMetadata original = new FileMetadata("key", "same.txt", 100, "chk", format, location);
            FileMetadata copy = original.withName("same.txt");

            assertEquals(original, copy);
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equals_sameValues() {
            FileMetadata a = new FileMetadata("key", "file.txt", 100, "chk", format, location);
            FileMetadata b = new FileMetadata("key", "file.txt", 100, "chk", format, location);
            assertEquals(a, b);
        }

        @Test
        void notEquals_differentKey() {
            FileMetadata a = new FileMetadata("key1", "file.txt", 100, "chk", format, location);
            FileMetadata b = new FileMetadata("key2", "file.txt", 100, "chk", format, location);
            assertNotEquals(a, b);
        }

        @Test
        void notEquals_differentSize() {
            FileMetadata a = new FileMetadata("key", "file.txt", 100, "chk", format, location);
            FileMetadata b = new FileMetadata("key", "file.txt", 200, "chk", format, location);
            assertNotEquals(a, b);
        }

        @Test
        void hashCode_sameValues() {
            FileMetadata a = new FileMetadata("key", "file.txt", 100, "chk", format, location);
            FileMetadata b = new FileMetadata("key", "file.txt", 100, "chk", format, location);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
