package io.github.dornol.filekit.validator;

import java.io.IOException;
import java.io.InputStream;

/**
 * Framework-agnostic abstraction for an uploaded file.
 *
 * <p>Implementations wrap platform-specific file representations
 * (e.g. Spring's {@code MultipartFile}) so that core validation
 * logic remains independent of any web framework.</p>
 *
 * @see io.github.dornol.filekit.spring.validator.MultipartFileSource
 */
public interface FileSource {

    /**
     * Returns the original filename as provided by the client.
     *
     * @return the original filename, or {@code null} if not available
     */
    String getOriginalFilename();

    /**
     * Returns an {@link InputStream} to read the file content.
     *
     * @return input stream of the file
     * @throws IOException if the stream cannot be opened
     */
    InputStream getInputStream() throws IOException;

    /**
     * Returns the size of the file in bytes.
     *
     * @return file size in bytes
     */
    long getSize();

    /**
     * Returns whether the file is empty (zero bytes).
     *
     * @return {@code true} if the file is empty
     */
    boolean isEmpty();

}
