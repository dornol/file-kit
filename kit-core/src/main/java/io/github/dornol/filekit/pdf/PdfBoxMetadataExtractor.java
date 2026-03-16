package io.github.dornol.filekit.pdf;

import io.github.dornol.filekit.storage.FileStorageException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.util.Calendar;

/**
 * {@link PdfMetadataExtractor} implementation using Apache PDFBox.
 */
public class PdfBoxMetadataExtractor implements PdfMetadataExtractor {

    @Override
    public PdfMetadata extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDDocumentInformation info = document.getDocumentInformation();

            Calendar creationCal = info.getCreationDate();

            return new PdfMetadata(
                    document.getNumberOfPages(),
                    info.getTitle(),
                    info.getAuthor(),
                    info.getCreator(),
                    creationCal != null ? creationCal.toInstant() : null
            );
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.PDF_PROCESSING_FAILED,
                    "Failed to extract PDF metadata", e);
        }
    }
}
