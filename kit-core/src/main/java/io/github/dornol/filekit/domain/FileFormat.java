package io.github.dornol.filekit.domain;

import java.util.Objects;

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
    public FileFormat {
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(primaryType, "primaryType");
        if (mimeType.isBlank() || extension.isBlank() || primaryType.isBlank()
                || mimeType.chars().anyMatch(Character::isISOControl)
                || extension.chars().anyMatch(Character::isISOControl)
                || primaryType.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("File format fields must be non-blank and printable");
        }
    }
}
