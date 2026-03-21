package io.github.dornol.filekit.domain;

import java.io.InputStream;
import java.util.Objects;

/**
 * Result of a file download containing metadata and the content stream.
 *
 * @param metadata file metadata
 * @param content  input stream to read the file content
 */
public record DownloadResult(
        FileMetadata metadata,
        InputStream content
) {
    public DownloadResult {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(content, "content");
    }
}
