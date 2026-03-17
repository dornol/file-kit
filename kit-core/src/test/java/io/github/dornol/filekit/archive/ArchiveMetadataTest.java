package io.github.dornol.filekit.archive;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveMetadataTest {

    @Nested
    class Validation {

        @Test
        void validMetadata() {
            List<ArchiveEntry> entries = List.of(
                    new ArchiveEntry("file.txt", 50, 100, null, false)
            );
            ArchiveMetadata metadata = new ArchiveMetadata(1, 100, entries);

            assertEquals(1, metadata.entryCount());
            assertEquals(100, metadata.totalUncompressedSize());
            assertEquals(1, metadata.entries().size());
        }

        @Test
        void multipleEntries() {
            List<ArchiveEntry> entries = List.of(
                    new ArchiveEntry("a.txt", 30, 60, null, false),
                    new ArchiveEntry("b.txt", 50, 100, null, false),
                    new ArchiveEntry("dir/", 0, 0, null, true)
            );
            ArchiveMetadata metadata = new ArchiveMetadata(3, 160, entries);

            assertEquals(3, metadata.entryCount());
            assertEquals(160, metadata.totalUncompressedSize());
            assertEquals(3, metadata.entries().size());
            assertEquals("a.txt", metadata.entries().get(0).path());
            assertEquals("b.txt", metadata.entries().get(1).path());
            assertEquals("dir/", metadata.entries().get(2).path());
        }

        @Test
        void negativeEntryCount_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArchiveMetadata(-1, 0, List.of()));
        }

        @Test
        void negativeTotalSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArchiveMetadata(0, -1, List.of()));
        }

        @Test
        void nullEntries_throws() {
            assertThrows(NullPointerException.class,
                    () -> new ArchiveMetadata(0, 0, null));
        }

        @Test
        void nullEntryInList_throws() {
            List<ArchiveEntry> entries = new ArrayList<>();
            entries.add(null);

            assertThrows(NullPointerException.class,
                    () -> new ArchiveMetadata(1, 0, entries));
        }

        @Test
        void emptyMetadata() {
            ArchiveMetadata metadata = new ArchiveMetadata(0, 0, List.of());
            assertEquals(0, metadata.entryCount());
            assertEquals(0, metadata.totalUncompressedSize());
            assertEquals(0, metadata.entries().size());
        }

        @Test
        void zeroEntryCountWithZeroSize() {
            ArchiveMetadata metadata = new ArchiveMetadata(0, 0, List.of());
            assertEquals(0, metadata.entryCount());
            assertEquals(0, metadata.totalUncompressedSize());
        }

        @Test
        void largeTotalSize() {
            long largeSize = 100L * 1024 * 1024 * 1024; // 100GB
            ArchiveMetadata metadata = new ArchiveMetadata(1, largeSize, List.of(
                    new ArchiveEntry("huge.bin", largeSize / 2, largeSize, null, false)
            ));
            assertEquals(largeSize, metadata.totalUncompressedSize());
        }
    }

    @Nested
    class DefensiveCopy {

        @Test
        void entriesAreDefensivelyCopied() {
            List<ArchiveEntry> mutableList = new ArrayList<>();
            mutableList.add(new ArchiveEntry("file.txt", 50, 100, null, false));

            ArchiveMetadata metadata = new ArchiveMetadata(1, 100, mutableList);
            mutableList.add(new ArchiveEntry("another.txt", 30, 60, null, false));

            assertEquals(1, metadata.entries().size());
        }

        @Test
        void entriesAreUnmodifiable() {
            ArchiveMetadata metadata = new ArchiveMetadata(1, 100,
                    List.of(new ArchiveEntry("file.txt", 50, 100, null, false)));

            assertThrows(UnsupportedOperationException.class,
                    () -> metadata.entries().add(new ArchiveEntry("x.txt", 0, 0, null, false)));
        }

        @Test
        void entriesCannotBeRemoved() {
            ArchiveMetadata metadata = new ArchiveMetadata(1, 100,
                    List.of(new ArchiveEntry("file.txt", 50, 100, null, false)));

            assertThrows(UnsupportedOperationException.class,
                    () -> metadata.entries().remove(0));
        }

        @Test
        void entriesCannotBeCleared() {
            ArchiveMetadata metadata = new ArchiveMetadata(1, 100,
                    List.of(new ArchiveEntry("file.txt", 50, 100, null, false)));

            assertThrows(UnsupportedOperationException.class,
                    () -> metadata.entries().clear());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameValues() {
            ArchiveEntry entry = new ArchiveEntry("file.txt", 50, 100, null, false);
            ArchiveMetadata a = new ArchiveMetadata(1, 100, List.of(entry));
            ArchiveMetadata b = new ArchiveMetadata(1, 100, List.of(entry));

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality_differentEntryCount() {
            ArchiveMetadata a = new ArchiveMetadata(1, 0, List.of());
            ArchiveMetadata b = new ArchiveMetadata(2, 0, List.of());

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentTotalSize() {
            ArchiveMetadata a = new ArchiveMetadata(0, 100, List.of());
            ArchiveMetadata b = new ArchiveMetadata(0, 200, List.of());

            assertNotEquals(a, b);
        }

        @Test
        void inequality_differentEntries() {
            ArchiveMetadata a = new ArchiveMetadata(1, 100,
                    List.of(new ArchiveEntry("a.txt", 50, 100, null, false)));
            ArchiveMetadata b = new ArchiveMetadata(1, 100,
                    List.of(new ArchiveEntry("b.txt", 50, 100, null, false)));

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsAllFields() {
            ArchiveMetadata metadata = new ArchiveMetadata(2, 500, List.of(
                    new ArchiveEntry("a.txt", 50, 100, null, false),
                    new ArchiveEntry("b.txt", 100, 400, null, false)
            ));
            String str = metadata.toString();
            assertNotNull(str);
            assertTrue(str.contains("2"));
            assertTrue(str.contains("500"));
        }
    }
}
