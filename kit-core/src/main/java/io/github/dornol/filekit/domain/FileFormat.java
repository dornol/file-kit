package io.github.dornol.filekit.domain;

/**
 * Detected format information for an uploaded file.
 *
 * @param mimeType    full MIME type (e.g. {@code "image/jpeg"})
 * @param extension   file extension without dot (e.g. {@code "jpg"})
 * @param primaryType primary MIME type (e.g. {@code "image"})
 */
public record FileFormat(
        String mimeType,
        String extension,
        String primaryType
) {
}
