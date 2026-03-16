package io.github.dornol.filekit.pdf;

import io.github.dornol.filekit.storage.FileStorageException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxMetadataExtractorTest {

    private final PdfBoxMetadataExtractor extractor = new PdfBoxMetadataExtractor();

    static byte[] createTestPdf(int pageCount, String title, String author) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }

            PDDocumentInformation info = document.getDocumentInformation();
            if (title != null) info.setTitle(title);
            if (author != null) info.setAuthor(author);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    static byte[] createTestPdfFull(int pageCount, String title, String author,
                                     String creator, Calendar creationDate) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }

            PDDocumentInformation info = document.getDocumentInformation();
            if (title != null) info.setTitle(title);
            if (author != null) info.setAuthor(author);
            if (creator != null) info.setCreator(creator);
            if (creationDate != null) info.setCreationDate(creationDate);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    @Nested
    class PageCount {

        @Test
        void singlePage() throws IOException {
            byte[] pdf = createTestPdf(1, null, null);

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals(1, meta.pageCount());
        }

        @Test
        void multiplePages() throws IOException {
            byte[] pdf = createTestPdf(5, null, null);

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals(5, meta.pageCount());
        }

        @Test
        void manyPages() throws IOException {
            byte[] pdf = createTestPdf(20, null, null);

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals(20, meta.pageCount());
        }
    }

    @Nested
    class DocumentInfo {

        @Test
        void extractsTitleAndAuthor() throws IOException {
            byte[] pdf = createTestPdf(1, "My Document", "John Doe");

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals("My Document", meta.title());
            assertEquals("John Doe", meta.author());
        }

        @Test
        void nullFieldsWhenNotSet() throws IOException {
            byte[] pdf = createTestPdf(1, null, null);

            PdfMetadata meta = extractor.extract(pdf);

            assertNull(meta.title());
            assertNull(meta.author());
        }

        @Test
        void extractsCreator() throws IOException {
            byte[] pdf = createTestPdfFull(1, null, null, "MyApp v1.0", null);

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals("MyApp v1.0", meta.creator());
        }

        @Test
        void extractsAllFields() throws IOException {
            Calendar cal = new GregorianCalendar(2024, Calendar.MARCH, 15, 10, 30, 0);
            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            byte[] pdf = createTestPdfFull(3, "Report", "Jane", "PDFGenerator", cal);

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals(3, meta.pageCount());
            assertEquals("Report", meta.title());
            assertEquals("Jane", meta.author());
            assertEquals("PDFGenerator", meta.creator());
            assertNotNull(meta.creationDate());
        }

        @Test
        void emptyStringTitle() throws IOException {
            byte[] pdf = createTestPdf(1, "", null);

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals("", meta.title());
        }

        @Test
        void specialCharactersInTitle() throws IOException {
            byte[] pdf = createTestPdf(1, "Report 2024 — \"Summary\" (한국어)", null);

            PdfMetadata meta = extractor.extract(pdf);

            assertEquals("Report 2024 — \"Summary\" (한국어)", meta.title());
        }
    }

    @Nested
    class CreationDate {

        @Test
        void extractsCreationDate() throws IOException {
            Calendar cal = new GregorianCalendar(2024, Calendar.MARCH, 15);
            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            byte[] pdf = createTestPdfFull(1, null, null, null, cal);

            PdfMetadata meta = extractor.extract(pdf);

            assertNotNull(meta.creationDate());
        }

        @Test
        void nullCreationDate_whenNotSet() throws IOException {
            byte[] pdf = createTestPdf(1, null, null);

            PdfMetadata meta = extractor.extract(pdf);

            assertNull(meta.creationDate());
        }

        @Test
        void creationDate_isInstant() throws IOException {
            Calendar cal = new GregorianCalendar(2020, Calendar.JANUARY, 1, 0, 0, 0);
            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            byte[] pdf = createTestPdfFull(1, null, null, null, cal);

            PdfMetadata meta = extractor.extract(pdf);

            assertNotNull(meta.creationDate());
            assertTrue(meta.creationDate().isBefore(Instant.now()));
        }
    }

    @Nested
    class InputStreamExtraction {

        @Test
        void extractFromInputStream() throws IOException {
            byte[] pdf = createTestPdf(3, "Stream Test", "Author");

            PdfMetadata meta = extractor.extract(new ByteArrayInputStream(pdf));

            assertEquals(3, meta.pageCount());
            assertEquals("Stream Test", meta.title());
            assertEquals("Author", meta.author());
        }

        @Test
        void extractFromInputStream_singlePage() throws IOException {
            byte[] pdf = createTestPdf(1, null, null);

            PdfMetadata meta = extractor.extract(new ByteArrayInputStream(pdf));

            assertEquals(1, meta.pageCount());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void invalidBytes_throws() {
            byte[] invalid = "not a pdf".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(invalid));
            assertEquals(FileStorageException.PDF_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void emptyBytes_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(new byte[0]));
            assertEquals(FileStorageException.PDF_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void randomBytes_throws() {
            byte[] random = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05};

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(random));
            assertEquals(FileStorageException.PDF_PROCESSING_FAILED, ex.getMessageKey());
        }

        @Test
        void truncatedPdfHeader_throws() {
            byte[] truncated = "%PDF-1.4".getBytes();

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> extractor.extract(truncated));
            assertEquals(FileStorageException.PDF_PROCESSING_FAILED, ex.getMessageKey());
        }
    }
}
