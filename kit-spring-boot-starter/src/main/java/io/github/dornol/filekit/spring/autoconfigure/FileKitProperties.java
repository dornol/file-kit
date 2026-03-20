package io.github.dornol.filekit.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for file-kit.
 *
 * <p>Example {@code application.yml}:</p>
 * <pre>{@code
 * file-kit:
 *   max-upload-size: 10485760  # 10MB, 0 = unlimited
 * }</pre>
 */
@ConfigurationProperties(prefix = "file-kit")
public class FileKitProperties {

    /**
     * Maximum upload file size in bytes. {@code 0} means unlimited.
     */
    private long maxUploadSize = 0;

    public long getMaxUploadSize() {
        return maxUploadSize;
    }

    public void setMaxUploadSize(long maxUploadSize) {
        if (maxUploadSize < 0) {
            throw new IllegalArgumentException("maxUploadSize must not be negative: " + maxUploadSize);
        }
        this.maxUploadSize = maxUploadSize;
    }

}
