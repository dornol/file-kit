package io.github.dornol.filekit.storage.local;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Strategy for computing the object key (relative path) of a stored file.
 *
 * <p>Use the built-in factory methods or implement your own:</p>
 * <ul>
 *   <li>{@link #flat()} &mdash; {@code key.ext}</li>
 *   <li>{@link #dateBased()} &mdash; {@code 2026/03/15/key.ext}</li>
 *   <li>{@link #hashPrefixed(int)} &mdash; {@code 73/3e/key.ext}</li>
 * </ul>
 */
@FunctionalInterface
public interface ObjectKeyStrategy {

    /**
     * Resolves the object key for a file.
     *
     * @param key       unique file key (typically UUID)
     * @param extension file extension without dot
     * @return relative path to use as the object key (may contain {@code /} for subdirectories)
     */
    String resolve(String key, String extension);

    /**
     * Flat strategy: stores all files in the bucket root.
     * <p>Example: {@code 733e0aee-c72c-4b6b-b152-c55cc44ea72f.png}</p>
     */
    static ObjectKeyStrategy flat() {
        return (key, ext) -> key + "." + ext;
    }

    /**
     * Date-based strategy: organizes files by date ({@code yyyy/MM/dd}).
     * <p>Example: {@code 2026/03/15/733e0aee-c72c-4b6b-b152-c55cc44ea72f.png}</p>
     */
    static ObjectKeyStrategy dateBased() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return (key, ext) -> LocalDate.now().format(formatter) + "/" + key + "." + ext;
    }

    /**
     * Hash-prefixed strategy: uses leading characters of the key as subdirectory levels.
     * Distributes files across directories to avoid filesystem bottlenecks.
     *
     * <p>Example with depth 2: {@code 73/3e/733e0aee-c72c-4b6b-b152-c55cc44ea72f.png}</p>
     *
     * @param depth number of 2-character prefix levels (1–4)
     * @throws IllegalArgumentException if depth is out of range
     */
    static ObjectKeyStrategy hashPrefixed(int depth) {
        if (depth < 1 || depth > 4) {
            throw new IllegalArgumentException("depth must be between 1 and 4, got: " + depth);
        }
        return (key, ext) -> {
            String normalized = key.replace("-", "");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                int start = i * 2;
                sb.append(normalized, start, Math.min(start + 2, normalized.length())).append('/');
            }
            sb.append(key).append('.').append(ext);
            return sb.toString();
        };
    }

}
