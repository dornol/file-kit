package io.github.dornol.filekit.example.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration for the example S3/MinIO storage. */
@Validated
@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {

    @NotBlank
    private String endpoint;

    @NotBlank
    private String region;

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    private boolean forcePathStyle = true;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public boolean isForcePathStyle() { return forcePathStyle; }
    public void setForcePathStyle(boolean forcePathStyle) { this.forcePathStyle = forcePathStyle; }
}
