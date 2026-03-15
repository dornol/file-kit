package io.github.dornol.filekit.domain;

import java.io.InputStream;

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
}
