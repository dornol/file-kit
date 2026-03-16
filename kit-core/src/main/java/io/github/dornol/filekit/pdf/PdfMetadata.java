package io.github.dornol.filekit.pdf;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Metadata extracted from a PDF document.
 *
 * @param pageCount    number of pages
 * @param title        document title (may be null)
 * @param author       document author (may be null)
 * @param creator      creator application (may be null)
 * @param creationDate document creation date (may be null)
 */
public record PdfMetadata(
        int pageCount,
        @Nullable String title,
        @Nullable String author,
        @Nullable String creator,
        @Nullable Instant creationDate
) {
    public PdfMetadata {
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must not be negative: " + pageCount);
        }
    }
}
