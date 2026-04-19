# S3 Storage (`S3FileStorage`)

Full reference implementation of `FileStorage` for S3 and S3-compatible backends (MinIO, Cloudflare R2, Wasabi, etc.).

## When to use

- Production object storage on AWS S3.
- Any S3-compatible API (MinIO for self-hosted, R2, Wasabi, Backblaze B2).
- Need pre-signed URLs for direct-to-S3 downloads.

The `example` module contains this code as a working reference.

## 1. Add AWS SDK dependency

```groovy
// Gradle
implementation platform('software.amazon.awssdk:bom:2.31.x')
implementation 'software.amazon.awssdk:s3'
```

```xml
<!-- Maven -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.31.x</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

## 2. Define storage type

```java
public enum StorageType { LOCAL, S3 }
```

## 3. Implement `FileStorage`

`FileUploadCommand.content()` exposes an `InputStream` — stream directly to S3, no need to buffer in memory:

```java
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3FileStorage(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public Enum<?> getStorageType() { return StorageType.S3; }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = command.key() + "." + command.extension();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(command.bucket())
                            .key(objectKey)
                            .contentType(command.mimeType())
                            .contentLength(command.contentLength())
                            .build(),
                    RequestBody.fromInputStream(command.content(), command.contentLength()));
            return new FileLocation(command.bucket(), objectKey, StorageType.S3);
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "S3 upload failed: " + objectKey, e);
        }
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(metadata.location().bucket())
                    .key(metadata.location().objectKey())
                    .build());
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "S3 download failed: " + metadata.location().objectKey(), e);
        }
    }

    @Override
    public void delete(FileMetadata metadata) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(metadata.location().bucket())
                    .key(metadata.location().objectKey())
                    .build());
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.DELETE_FAILED,
                    "S3 delete failed: " + metadata.location().objectKey(), e);
        }
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return "/files/" + metadata.key() + "/download";
    }

    @Override
    public String generatePresignedUrl(FileMetadata metadata, Duration expiration) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(metadata.location().bucket())
                            .key(metadata.location().objectKey())
                            .build())
                    .build();
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.PRESIGNED_URL_FAILED,
                    "Failed to generate pre-signed URL: " + metadata.location().objectKey(), e);
        }
    }
}
```

## 4. Configure `S3Client` and register the bean

```java
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${app.s3.endpoint}") String endpoint,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${app.s3.endpoint}") String endpoint,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    public FileStorage s3FileStorage(S3Client s3Client, S3Presigner s3Presigner) {
        return new S3FileStorage(s3Client, s3Presigner);
    }
}
```

```yaml
# application.yml
app:
  s3:
    endpoint: http://localhost:9000   # MinIO
    region: us-east-1
    access-key: minioadmin
    secret-key: minioadmin
```

## S3-compatible services

This implementation works with any S3-compatible storage:

- [MinIO](https://min.io/) — self-hosted
- [Cloudflare R2](https://developers.cloudflare.com/r2/)
- [Wasabi](https://wasabi.com/)
- [Backblaze B2](https://www.backblaze.com/b2/)

Change `endpoint` and credentials. For **AWS S3**, remove `endpointOverride()` and `forcePathStyle()`, and use the default credential provider chain instead of static credentials.

## Other backends (GCS, Azure Blob, etc.)

Same pattern applies — implement the five `FileStorage` methods and register as a Spring bean.

## Related

- [storage-spi.md](storage-spi.md) — SPI contract and multi-backend setup.
- [download.md](download.md) — pre-signed URL generation via `FileDownloadService`.
