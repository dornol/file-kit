package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileMetadata;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

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
 * // Custom content type
 * return FileResponseBuilder.download("report.xlsx")
 *         .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
 *         .body(resource);
 * }</pre>
 */
public final class FileResponseBuilder {

    private final String filename;
    private final FileFetchAction action;
    private String contentType;
    private Long contentLength;
    private Duration cacheDuration;

    private FileResponseBuilder(String filename, FileFetchAction action) {
        this.filename = filename;
        this.action = action;
    }

    /**
     * Creates a builder for a file download response (Content-Disposition: attachment).
     *
     * @param metadata file metadata (uses name, format, size)
     */
    public static FileResponseBuilder download(FileMetadata metadata) {
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
        this.contentLength = contentLength;
        return this;
    }

    /**
     * Sets the Cache-Control max-age header.
     *
     * @param duration cache duration
     */
    public FileResponseBuilder cache(Duration duration) {
        this.cacheDuration = duration;
        return this;
    }

    /**
     * Builds the {@link ResponseEntity.BodyBuilder} with all configured headers.
     * Call {@code .body(resource)} on the result to complete the response.
     *
     * @return configured ResponseEntity.BodyBuilder
     */
    public ResponseEntity.BodyBuilder toResponseBuilder() {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition());

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
        String fallback = filename.replaceAll("[^\\x20-\\x7E]", "_");

        String encoded;
        try {
            encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            encoded = fallback;
        }

        return String.format(Locale.ROOT,
                "%s; filename=\"%s\"; filename*=UTF-8''%s",
                action.getDispositionType(), fallback, encoded);
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
