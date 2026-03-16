package io.github.dornol.filekit.scan;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * SPI for virus scanning.
 *
 * <p>When registered, the {@link io.github.dornol.filekit.upload.FileUploadService}
 * will invoke {@link #scan(InputStream)} before proceeding with the upload.
 * If the result is {@link ScanResult.Status#INFECTED} or {@link ScanResult.Status#ERROR},
 * the upload is rejected (fail-closed). To implement fail-open semantics,
 * return {@link ScanResult#clean()} from your implementation when errors occur.</p>
 */
public interface VirusScanner {

    /**
     * Scans the given file bytes for viruses.
     *
     * @param fileBytes the raw file content
     * @return the scan result
     */
    ScanResult scan(byte[] fileBytes);

    /**
     * Scans the given input stream for viruses.
     *
     * <p>The default implementation reads all bytes into memory.
     * Override for a streaming implementation (e.g. ClamAV INSTREAM).</p>
     *
     * @param inputStream the file content stream
     * @return the scan result
     */
    default ScanResult scan(InputStream inputStream) {
        try {
            return scan(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
