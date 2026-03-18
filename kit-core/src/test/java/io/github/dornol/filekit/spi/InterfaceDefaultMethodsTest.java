package io.github.dornol.filekit.spi;

import io.github.dornol.filekit.archive.ArchiveEntry;
import io.github.dornol.filekit.archive.ArchiveMetadata;
import io.github.dornol.filekit.archive.ArchiveMetadataExtractor;
import io.github.dornol.filekit.pdf.PdfMetadata;
import io.github.dornol.filekit.pdf.PdfMetadataExtractor;
import io.github.dornol.filekit.storage.FileStorageException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for default methods on SPI interfaces:
 * {@link PdfMetadataExtractor#extract(InputStream)},
 * {@link ArchiveMetadataExtractor#extract(InputStream)},
 * {@link ChecksumCalculator#checksum(InputStream)}.
 */
class InterfaceDefaultMethodsTest {

    // ── PdfMetadataExtractor default method ─────────────────────────

    @Nested
    class PdfMetadataExtractorDefault {

        /** Implementation that only provides byte[] extract. */
        private final PdfMetadataExtractor extractor = pdfBytes ->
                new PdfMetadata(pdfBytes.length, null, null, null, null);

        @Test
        void extractFromStream_delegatesToByteArray() {
            byte[] content = "fake-pdf".getBytes();

            PdfMetadata result = extractor.extract(new ByteArrayInputStream(content));

            assertNotNull(result);
            assertEquals(content.length, result.pageCount());
        }

        @Test
        void extractFromStream_brokenStream_throwsFileStorageException() {
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("broken");
                }
                @Override
                public byte[] readAllBytes() throws IOException {
                    throw new IOException("broken");
                }
            };

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(broken));
            assertEquals(FileStorageException.PDF_PROCESSING_FAILED, ex.getMessageKey());
        }
    }

    // ── ArchiveMetadataExtractor default method ─────────────────────

    @Nested
    class ArchiveMetadataExtractorDefault {

        /** Implementation that only provides byte[] extract. */
        private final ArchiveMetadataExtractor extractor = archiveBytes ->
                new ArchiveMetadata(1, archiveBytes.length, List.of(
                        new ArchiveEntry("file.txt", archiveBytes.length, archiveBytes.length, null, false)));

        @Test
        void extractFromStream_delegatesToByteArray() {
            byte[] content = "fake-archive-content".getBytes();

            ArchiveMetadata result = extractor.extract(new ByteArrayInputStream(content));

            assertNotNull(result);
            assertEquals(1, result.entryCount());
            assertEquals(content.length, result.totalUncompressedSize());
        }

        @Test
        void extractFromStream_brokenStream_throwsFileStorageException() {
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("broken");
                }
                @Override
                public byte[] readAllBytes() throws IOException {
                    throw new IOException("broken");
                }
            };

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(broken));
            assertEquals(FileStorageException.ARCHIVE_PROCESSING_FAILED, ex.getMessageKey());
        }
    }

    // ── ChecksumCalculator default method ───────────────────────────

    @Nested
    class ChecksumCalculatorDefault {

        /** Implementation that only provides byte[] checksum. */
        private final ChecksumCalculator calculator = bytes -> "hash-" + bytes.length;

        @Test
        void checksumFromStream_delegatesToByteArray() {
            byte[] content = "test content".getBytes();

            String result = calculator.checksum(new ByteArrayInputStream(content));

            assertEquals("hash-" + content.length, result);
        }

        @Test
        void checksumFromStream_emptyStream() {
            String result = calculator.checksum(new ByteArrayInputStream(new byte[0]));
            assertEquals("hash-0", result);
        }
    }
}
