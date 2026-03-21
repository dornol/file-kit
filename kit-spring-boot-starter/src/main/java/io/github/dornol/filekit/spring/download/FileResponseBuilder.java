package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.ByteRange;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorageException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility for building file download/inline HTTP responses with proper headers.
 *
 * <pre>{@code
 * // Download with auto-detected content type
 * return FileResponseBuilder.download(metadata)
 *         .body(resource);
 *
 * // Inline (browser preview) with cache
 * return FileResponseBuilder.inline(metadata)
 *         .cache(Duration.ofHours(1))
 *         .body(resource);
 *
 * // Range request support
 * return FileResponseBuilder.inline(metadata)
 *         .range(rangeHeaderValue)
 *         .body(resource);
 * }</pre>
 */
public final class FileResponseBuilder {

    private final String filename;
    private final FileFetchAction action;
    private @Nullable String contentType;
    private @Nullable Long contentLength;
    private @Nullable Duration cacheDuration;
    private @Nullable String rangeHeaderValue;

    private FileResponseBuilder(String filename, FileFetchAction action) {
        Objects.requireNonNull(filename, "filename");
        this.filename = filename.replaceAll("[\\p{Cntrl}]", "");
        this.action = action;
    }

    /**
     * Creates a builder for a file download response (Content-Disposition: attachment).
     *
     * @param metadata file metadata (uses name, format, size)
     */
    public static FileResponseBuilder download(FileMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        FileResponseBuilder builder = new FileResponseBuilder(metadata.name(), FileFetchAction.DOWNLOAD);
        builder.contentType = metadata.format().mimeType();
        builder.contentLength = metadata.size();
        return builder;
    }

    /**
     * Creates a builder for a file download response with a custom filename.
     *
     * @param filename download filename
     */
    public static FileResponseBuilder download(String filename) {
        return new FileResponseBuilder(filename, FileFetchAction.DOWNLOAD);
    }

    /**
     * Creates a builder for an inline response (Content-Disposition: inline).
     * The browser will try to preview the file if possible.
     *
     * @param metadata file metadata (uses name, format, size)
     */
    public static FileResponseBuilder inline(FileMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        FileResponseBuilder builder = new FileResponseBuilder(metadata.name(), FileFetchAction.INLINE);
        builder.contentType = metadata.format().mimeType();
        builder.contentLength = metadata.size();
        return builder;
    }

    /**
     * Creates a builder for an inline response with a custom filename.
     *
     * @param filename inline filename
     */
    public static FileResponseBuilder inline(String filename) {
        return new FileResponseBuilder(filename, FileFetchAction.INLINE);
    }

    /**
     * Sets the Content-Type header.
     *
     * @param contentType MIME type string
     */
    public FileResponseBuilder contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * Sets the Content-Length header.
     *
     * @param contentLength file size in bytes
     */
    public FileResponseBuilder contentLength(long contentLength) {
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative: " + contentLength);
        }
        this.contentLength = contentLength;
        return this;
    }

    /**
     * Sets the Cache-Control max-age header.
     *
     * @param duration cache duration
     */
    public FileResponseBuilder cache(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        this.cacheDuration = duration;
        return this;
    }

    /**
     * Sets the Range header value for partial content responses.
     * When set and valid, the response will be 206 Partial Content.
     *
     * @param rangeHeaderValue the Range header value (e.g. "bytes=0-499"), or null for full content
     */
    public FileResponseBuilder range(@Nullable String rangeHeaderValue) {
        this.rangeHeaderValue = rangeHeaderValue;
        return this;
    }

    /**
     * Builds the {@link ResponseEntity.BodyBuilder} with all configured headers.
     * Call {@code .body(resource)} on the result to complete the response.
     *
     * @return configured ResponseEntity.BodyBuilder
     */
    public ResponseEntity.BodyBuilder toResponseBuilder() {
        if (rangeHeaderValue != null && contentLength != null) {
            try {
                ByteRange byteRange = ByteRange.parse(rangeHeaderValue, contentLength);
                return buildRangeResponse(byteRange);
            } catch (FileStorageException e) {
                // 416 Range Not Satisfiable
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength);
            }
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (contentType != null) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        if (contentLength != null) {
            builder.contentLength(contentLength);
        }
        if (cacheDuration != null) {
            builder.cacheControl(CacheControl.maxAge(cacheDuration));
        }

        return builder;
    }

    private ResponseEntity.BodyBuilder buildRangeResponse(ByteRange byteRange) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, byteRange.toContentRangeHeader())
                .contentLength(byteRange.length());

        if (contentType != null) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        if (cacheDuration != null) {
            builder.cacheControl(CacheControl.maxAge(cacheDuration));
        }

        return builder;
    }

    /**
     * Convenience method: builds the response and sets the body in one call.
     *
     * @param body response body (typically a Spring {@link Resource})
     * @return complete ResponseEntity
     */
    public <T> ResponseEntity<T> body(T body) {
        return toResponseBuilder().body(body);
    }

    private String buildContentDisposition() {
        // ASCII-only fallback: replace non-printable/non-ASCII chars with underscore
        String fallback = filename.replaceAll("[^\\x20-\\x7E]", "_");
        // Escape backslashes and double quotes per RFC 6266 / RFC 2616 quoted-string
        String escapedFallback = fallback.replace("\\", "\\\\").replace("\"", "\\\"");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return String.format(Locale.ROOT,
                "%s; filename=\"%s\"; filename*=UTF-8''%s",
                action.getDispositionType(), escapedFallback, encoded);
    }

    /**
     * File fetch action determining the Content-Disposition type.
     */
    public enum FileFetchAction {
        /** Browser downloads the file. */
        DOWNLOAD("attachment"),
        /** Browser previews the file inline if possible. */
        INLINE("inline");

        private final String dispositionType;

        FileFetchAction(String dispositionType) {
            this.dispositionType = dispositionType;
        }

        public String getDispositionType() {
            return dispositionType;
        }
    }

}
