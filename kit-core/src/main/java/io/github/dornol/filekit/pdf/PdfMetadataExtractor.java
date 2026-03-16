package io.github.dornol.filekit.pdf;

import java.io.IOException;
import java.io.InputStream;

/**
 * SPI for extracting metadata from PDF documents.
 */
public interface PdfMetadataExtractor {

    /**
     * Extracts metadata from PDF bytes.
     *
     * @param pdfBytes the raw PDF content
     * @return extracted metadata
     * @throws io.github.dornol.filekit.storage.FileStorageException if extraction fails
     */
    PdfMetadata extract(byte[] pdfBytes);

    /**
     * Extracts metadata from a PDF input stream.
     *
     * @param pdfStream the PDF input stream
     * @return extracted metadata
     * @throws io.github.dornol.filekit.storage.FileStorageException if extraction fails
     */
    default PdfMetadata extract(InputStream pdfStream) {
        try {
            return extract(pdfStream.readAllBytes());
        } catch (IOException e) {
            throw new io.github.dornol.filekit.storage.FileStorageException(
                    io.github.dornol.filekit.storage.FileStorageException.PDF_PROCESSING_FAILED,
                    "Failed to read PDF stream", e);
        }
    }
}
