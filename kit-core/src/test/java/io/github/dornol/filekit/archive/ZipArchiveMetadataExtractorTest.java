package io.github.dornol.filekit.archive;

import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipArchiveMetadataExtractorTest {

    ZipArchiveMetadataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ZipArchiveMetadataExtractor();
    }

    private byte[] createZip(ZipCreator creator) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            creator.create(zos);
        }
        return baos.toByteArray();
    }

    @FunctionalInterface
    interface ZipCreator {
        void create(ZipOutputStream zos) throws IOException;
    }

    // ── Single file ─────────────────────────────────────────────────

    @Nested
    class SingleFile {

        @Test
        void extractsSingleFileEntry() throws IOException {
            byte[] content = "hello world".getBytes();
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("hello.txt"));
                zos.write(content);
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(1, metadata.entryCount());
            assertEquals(content.length, metadata.totalUncompressedSize());

            ArchiveEntry entry = metadata.entries().get(0);
            assertEquals("hello.txt", entry.path());
            assertEquals(content.length, entry.uncompressedSize());
            assertFalse(entry.directory());
        }

        @Test
        void emptyFileEntry() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("empty.txt"));
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(1, metadata.entryCount());
            assertEquals(0, metadata.totalUncompressedSize());
            assertEquals(0, metadata.entries().get(0).uncompressedSize());
        }

        @Test
        void binaryContent() throws IOException {
            byte[] binaryData = new byte[256];
            for (int i = 0; i < 256; i++) binaryData[i] = (byte) i;

            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("binary.bin"));
                zos.write(binaryData);
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(1, metadata.entryCount());
            assertEquals(256, metadata.totalUncompressedSize());
        }
    }

    // ── Multiple files ──────────────────────────────────────────────

    @Nested
    class MultipleFiles {

        @Test
        void extractsMultipleEntries() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("a.txt"));
                zos.write("aaa".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("b.txt"));
                zos.write("bbbbb".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(2, metadata.entryCount());
            assertEquals(8, metadata.totalUncompressedSize());
        }

        @Test
        void preservesEntryOrder() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("third.txt"));
                zos.write("3".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("first.txt"));
                zos.write("1".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("second.txt"));
                zos.write("2".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(3, metadata.entryCount());
            assertEquals("third.txt", metadata.entries().get(0).path());
            assertEquals("first.txt", metadata.entries().get(1).path());
            assertEquals("second.txt", metadata.entries().get(2).path());
        }

        @Test
        void manyEntries() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                for (int i = 0; i < 100; i++) {
                    zos.putNextEntry(new ZipEntry("file-" + i + ".txt"));
                    zos.write(("content-" + i).getBytes());
                    zos.closeEntry();
                }
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(100, metadata.entryCount());
            assertTrue(metadata.totalUncompressedSize() > 0);
        }

        @Test
        void sameNameInDifferentDirectories() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("dirA/file.txt"));
                zos.write("a".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("dirB/file.txt"));
                zos.write("b".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(2, metadata.entryCount());
            assertEquals("dirA/file.txt", metadata.entries().get(0).path());
            assertEquals("dirB/file.txt", metadata.entries().get(1).path());
        }
    }

    // ── Directories ─────────────────────────────────────────────────

    @Nested
    class Directories {

        @Test
        void recognizesDirectoryEntries() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("dir/"));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("dir/file.txt"));
                zos.write("content".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(2, metadata.entryCount());
            assertTrue(metadata.entries().get(0).directory());
            assertFalse(metadata.entries().get(1).directory());
        }

        @Test
        void nestedDirectories() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("a/"));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("a/b/"));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("a/b/c/"));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("a/b/c/deep.txt"));
                zos.write("deep content".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(4, metadata.entryCount());
            assertTrue(metadata.entries().get(0).directory());
            assertTrue(metadata.entries().get(1).directory());
            assertTrue(metadata.entries().get(2).directory());
            assertFalse(metadata.entries().get(3).directory());
            assertEquals("a/b/c/deep.txt", metadata.entries().get(3).path());
        }

        @Test
        void directoryHasZeroUncompressedSize() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("dir/"));
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(0, metadata.entries().get(0).uncompressedSize());
            assertEquals(0, metadata.totalUncompressedSize());
        }
    }

    // ── Empty ZIP ───────────────────────────────────────────────────

    @Nested
    class EmptyZip {

        @Test
        void handlesEmptyArchive() throws IOException {
            byte[] zipBytes = createZip(zos -> {});

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(0, metadata.entryCount());
            assertEquals(0, metadata.totalUncompressedSize());
            assertTrue(metadata.entries().isEmpty());
        }
    }

    // ── InputStream extraction ──────────────────────────────────────

    @Nested
    class InputStreamExtraction {

        @Test
        void extractsFromInputStream() throws IOException {
            byte[] content = "stream content".getBytes();
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("stream.txt"));
                zos.write(content);
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(new ByteArrayInputStream(zipBytes));

            assertEquals(1, metadata.entryCount());
            assertEquals("stream.txt", metadata.entries().get(0).path());
            assertEquals(content.length, metadata.entries().get(0).uncompressedSize());
        }

        @Test
        void inputStreamWithMultipleEntries() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("one.txt"));
                zos.write("one".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("two.txt"));
                zos.write("two".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(new ByteArrayInputStream(zipBytes));

            assertEquals(2, metadata.entryCount());
        }
    }

    // ── Last modified ───────────────────────────────────────────────

    @Nested
    class LastModified {

        @Test
        void extractsLastModifiedTime() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                ZipEntry entry = new ZipEntry("timed.txt");
                zos.putNextEntry(entry);
                zos.write("data".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);
            assertNotNull(metadata.entries().get(0).lastModified());
        }

        @Test
        void eachEntryHasItsOwnLastModified() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                ZipEntry entry1 = new ZipEntry("first.txt");
                zos.putNextEntry(entry1);
                zos.write("first".getBytes());
                zos.closeEntry();

                ZipEntry entry2 = new ZipEntry("second.txt");
                zos.putNextEntry(entry2);
                zos.write("second".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertNotNull(metadata.entries().get(0).lastModified());
            assertNotNull(metadata.entries().get(1).lastModified());
        }
    }

    // ── Compressed size ─────────────────────────────────────────────

    @Nested
    class CompressedSize {

        @Test
        void compressedSizeIsNonNegative() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("file.txt"));
                zos.write("some content to compress".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);
            assertTrue(metadata.entries().get(0).compressedSize() >= 0);
        }

        @Test
        void uncompressedSizeMatchesOriginal() throws IOException {
            String content = "exact content length test";
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("measured.txt"));
                zos.write(content.getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);
            assertEquals(content.getBytes().length, metadata.entries().get(0).uncompressedSize());
        }

        @Test
        void totalUncompressedSizeIsSumOfEntries() throws IOException {
            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("a.txt"));
                zos.write("aaa".getBytes()); // 3 bytes
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("b.txt"));
                zos.write("bb".getBytes()); // 2 bytes
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("c.txt"));
                zos.write("cccccccccc".getBytes()); // 10 bytes
                zos.closeEntry();
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);

            assertEquals(15, metadata.totalUncompressedSize());
            long sum = metadata.entries().stream()
                    .mapToLong(ArchiveEntry::uncompressedSize)
                    .sum();
            assertEquals(metadata.totalUncompressedSize(), sum);
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    @Nested
    class ErrorHandling {

        @Test
        void invalidZipData_returnsEmpty() {
            // ZipInputStream silently ignores non-zip data and returns no entries
            byte[] invalidData = "not a zip file".getBytes();

            ArchiveMetadata metadata = extractor.extract(invalidData);
            assertEquals(0, metadata.entryCount());
            assertTrue(metadata.entries().isEmpty());
        }

        @Test
        void emptyByteArray_returnsEmpty() {
            ArchiveMetadata metadata = extractor.extract(new byte[0]);
            assertEquals(0, metadata.entryCount());
            assertTrue(metadata.entries().isEmpty());
        }

        @Test
        void randomBytes_returnsEmpty() {
            byte[] random = new byte[]{0x50, 0x4B, 0x00, 0x00, (byte) 0xFF, (byte) 0xFE};

            ArchiveMetadata metadata = extractor.extract(random);
            assertEquals(0, metadata.entryCount());
        }
    }

    // ── Zip bomb protection ────────────────────────────────────────

    @Nested
    class ZipBombProtection {

        @Test
        void rejectsArchiveExceedingMaxUncompressedSize() throws IOException {
            // Create extractor with 100-byte limit
            ZipArchiveMetadataExtractor limited = new ZipArchiveMetadataExtractor(100, 65_535);

            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("big.txt"));
                zos.write(new byte[200]); // 200 bytes > 100 limit
                zos.closeEntry();
            });

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> limited.extract(zipBytes));
            assertEquals(FileStorageException.ARCHIVE_PROCESSING_FAILED, ex.getMessageKey());
            assertTrue(ex.getMessage().contains("maximum uncompressed size"));
        }

        @Test
        void rejectsArchiveExceedingMaxEntries() throws IOException {
            // Create extractor with 3-entry limit
            ZipArchiveMetadataExtractor limited = new ZipArchiveMetadataExtractor(Long.MAX_VALUE, 3);

            byte[] zipBytes = createZip(zos -> {
                for (int i = 0; i < 5; i++) {
                    zos.putNextEntry(new ZipEntry("file-" + i + ".txt"));
                    zos.write(("content-" + i).getBytes());
                    zos.closeEntry();
                }
            });

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> limited.extract(zipBytes));
            assertEquals(FileStorageException.ARCHIVE_PROCESSING_FAILED, ex.getMessageKey());
            assertTrue(ex.getMessage().contains("maximum entry count"));
        }

        @Test
        void acceptsArchiveWithinLimits() throws IOException {
            ZipArchiveMetadataExtractor limited = new ZipArchiveMetadataExtractor(1000, 10);

            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("small.txt"));
                zos.write("hello".getBytes());
                zos.closeEntry();
            });

            ArchiveMetadata metadata = limited.extract(zipBytes);
            assertEquals(1, metadata.entryCount());
        }

        @Test
        void rejectsExceedingCumulativeSize() throws IOException {
            // 50 bytes total limit, two 30-byte entries → cumulative 60 > 50
            ZipArchiveMetadataExtractor limited = new ZipArchiveMetadataExtractor(50, 100);

            byte[] zipBytes = createZip(zos -> {
                zos.putNextEntry(new ZipEntry("a.txt"));
                zos.write(new byte[30]);
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("b.txt"));
                zos.write(new byte[30]);
                zos.closeEntry();
            });

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> limited.extract(zipBytes));
            assertTrue(ex.getMessage().contains("maximum uncompressed size"));
        }

        @Test
        void defaultLimitsAllowNormalArchives() throws IOException {
            // Default extractor should handle normal archives
            byte[] zipBytes = createZip(zos -> {
                for (int i = 0; i < 100; i++) {
                    zos.putNextEntry(new ZipEntry("file-" + i + ".txt"));
                    zos.write(("content-" + i).getBytes());
                    zos.closeEntry();
                }
            });

            ArchiveMetadata metadata = extractor.extract(zipBytes);
            assertEquals(100, metadata.entryCount());
        }
    }

    // ── Constructor validation ─────────────────────────────────────

    @Nested
    class ConstructorValidation {

        @Test
        void zeroMaxUncompressedSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZipArchiveMetadataExtractor(0, 100));
        }

        @Test
        void negativeMaxUncompressedSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZipArchiveMetadataExtractor(-1, 100));
        }

        @Test
        void zeroMaxEntries_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZipArchiveMetadataExtractor(1000, 0));
        }

        @Test
        void negativeMaxEntries_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZipArchiveMetadataExtractor(1000, -1));
        }

        @Test
        void validParameters_doesNotThrow() {
            ZipArchiveMetadataExtractor custom = new ZipArchiveMetadataExtractor(1024, 10);
            assertNotNull(custom);
        }
    }
}
