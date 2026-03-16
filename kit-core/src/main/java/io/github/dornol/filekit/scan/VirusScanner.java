package io.github.dornol.filekit.scan;

/**
 * SPI for virus scanning.
 *
 * <p>When registered, the {@link io.github.dornol.filekit.upload.FileUploadService}
 * will invoke {@link #scan(byte[])} before proceeding with the upload.
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
}
