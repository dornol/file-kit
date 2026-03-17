package io.github.dornol.filekit.archive;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveEntryTest {

    @Nested
    class Validation {

        @Test
        void validEntry() {
            Instant now = Instant.now();
            ArchiveEntry entry = new ArchiveEntry("file.txt", 100, 200, now, false);

            assertEquals("file.txt", entry.path());
            assertEquals(100, entry.compressedSize());
            assertEquals(200, entry.uncompressedSize());
            assertEquals(now, entry.lastModified());
            assertFalse(entry.directory());
        }

        @Test
        void directoryEntry() {
            ArchiveEntry entry = new ArchiveEntry("dir/", 0, 0, null, true);

            assertEquals("dir/", entry.path());
            assertTrue(entry.directory());
            assertNull(entry.lastModified());
        }

        @Test
        void nullPath_throws() {
            assertThrows(NullPointerException.class,
                    () -> new ArchiveEntry(null, 0, 0, null, false));
        }

        @Test
        void negativeCompressedSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArchiveEntry("file.txt", -1, 0, null, false));
        }

        @Test
        void negativeUncompressedSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArchiveEntry("file.txt", 0, -1, null, false));
        }

        @Test
        void zeroSizes_valid() {
            ArchiveEntry entry = new ArchiveEntry("empty.txt", 0, 0, null, false);
            assertEquals(0, entry.compressedSize());
            assertEquals(0, entry.uncompressedSize());
        }

        @Test
        void largeSizes_valid() {
            long largeSize = 10L * 1024 * 1024 * 1024; // 10GB
            ArchiveEntry entry = new ArchiveEntry("large.bin", largeSize, largeSize * 2, null, false);
            assertEquals(largeSize, entry.compressedSize());
            assertEquals(largeSize * 2, entry.uncompressedSize());
        }

        @Test
        void nestedPath_valid() {
            ArchiveEntry entry = new ArchiveEntry("a/b/c/d/file.txt", 10, 20, null, false);
            assertEquals("a/b/c/d/file.txt", entry.path());
        }

        @Test
        void emptyPath_valid() {
            ArchiveEntry entry = new ArchiveEntry("", 0, 0, null, false);
            assertEquals("", entry.path());
        }

        @Test
        void nullLastModified_allowed() {
            ArchiveEntry entry = new ArchiveEntry("file.txt", 0, 0, null, false);
            assertNull(entry.lastModified());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameValues() {
            Instant now = Instant.parse("2024-01-01T00:00:00Z");
            ArchiveEntry a = new ArchiveEntry("file.txt", 100, 200, now, false);
            ArchiveEntry b = new ArchiveEntry("file.txt", 100, 200, now, false);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality_differentPath() {
            ArchiveEntry a = new ArchiveEntry("a.txt", 100, 200, null, false);
            ArchiveEntry b = new ArchiveEntry("b.txt", 100, 200, null, false);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentCompressedSize() {
            ArchiveEntry a = new ArchiveEntry("file.txt", 100, 200, null, false);
            ArchiveEntry b = new ArchiveEntry("file.txt", 150, 200, null, false);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentUncompressedSize() {
            ArchiveEntry a = new ArchiveEntry("file.txt", 100, 200, null, false);
            ArchiveEntry b = new ArchiveEntry("file.txt", 100, 300, null, false);

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentDirectory() {
            ArchiveEntry a = new ArchiveEntry("dir/", 0, 0, null, false);
            ArchiveEntry b = new ArchiveEntry("dir/", 0, 0, null, true);

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsAllFields() {
            ArchiveEntry entry = new ArchiveEntry("file.txt", 100, 200, null, false);
            String str = entry.toString();
            assertNotNull(str);
            assertTrue(str.contains("file.txt"));
            assertTrue(str.contains("100"));
            assertTrue(str.contains("200"));
        }
    }
}
