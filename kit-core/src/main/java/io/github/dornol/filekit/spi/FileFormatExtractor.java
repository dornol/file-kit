package io.github.dornol.filekit.spi;

import io.github.dornol.filekit.domain.FileFormat;

import java.io.InputStream;

/**
 * Extracts file format information (MIME type, extension) from file content.
 */
public interface FileFormatExtractor {

    /**
     * Detects the format of a file from its content stream.
     *
     * @param inputStream file content
     * @return detected file format
     */
    FileFormat extract(InputStream inputStream);

}
