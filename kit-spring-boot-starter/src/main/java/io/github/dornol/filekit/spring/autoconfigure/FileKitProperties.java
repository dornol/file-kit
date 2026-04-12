package io.github.dornol.filekit.spring.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for file-kit.
 *
 * <p>Example {@code application.yml}:</p>
 * <pre>{@code
 * file-kit:
 *   max-upload-size: 10485760     # 10MB, 0 = unlimited
 *   verify-checksum-on-download: false  # verify integrity on download
 *   max-presigned-expiration: 1h  # maximum pre-signed URL lifetime
 * }</pre>
 */
@ConfigurationProperties(prefix = "file-kit")
public class FileKitProperties {

    /**
     * Maximum upload file size in bytes. {@code 0} means unlimited.
     */
    private long maxUploadSize = 0;

    /**
     * Whether to verify file checksum on download.
     * When enabled, downloaded content is checked against the stored checksum.
     */
    private boolean verifyChecksumOnDownload = false;

    /**
     * Maximum allowed expiration duration for pre-signed URLs.
     * {@code null} means no limit.
     */
    private @Nullable Duration maxPresignedExpiration;

    public long getMaxUploadSize() {
        return maxUploadSize;
    }

    public void setMaxUploadSize(long maxUploadSize) {
        if (maxUploadSize < 0) {
            throw new IllegalArgumentException("maxUploadSize must not be negative: " + maxUploadSize);
        }
        this.maxUploadSize = maxUploadSize;
    }

    public boolean isVerifyChecksumOnDownload() {
        return verifyChecksumOnDownload;
    }

    public void setVerifyChecksumOnDownload(boolean verifyChecksumOnDownload) {
        this.verifyChecksumOnDownload = verifyChecksumOnDownload;
    }

    public @Nullable Duration getMaxPresignedExpiration() {
        return maxPresignedExpiration;
    }

    public void setMaxPresignedExpiration(@Nullable Duration maxPresignedExpiration) {
        this.maxPresignedExpiration = maxPresignedExpiration;
    }

}
