package io.github.dornol.filekit.validator;

import java.util.Set;

/**
 * Represents an allowed media type with its associated file extensions.
 *
 * <p>Implement this interface as an {@code enum} and pass it to
 * {@link ValidFile#value()} to define which media types are accepted.</p>
 *
 * <pre>{@code
 * public enum AllowedMediaType implements SafeMediaType {
 *     JPEG("image/jpeg", Set.of("jpg", "jpeg")),
 *     PNG("image/png", Set.of("png"));
 *
 *     // ... constructor and getters
 * }
 * }</pre>
 */
public interface SafeMediaType {

    /**
     * Returns the MIME type string (e.g. {@code "image/png"}).
     *
     * @return MIME type
     */
    String getMediaType();

    /**
     * Returns the set of allowed file extensions for this media type (lowercase, without dot).
     *
     * @return set of extensions (e.g. {@code Set.of("jpg", "jpeg")})
     */
    Set<String> getExtensions();

}
