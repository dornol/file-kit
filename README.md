# file-kit

A lightweight Java library for file validation, upload, download, and deletion. Validates uploaded files by media type, file size, filename safety, and extension-content consistency. Provides a pluggable storage abstraction for uploading and downloading files with checksum-based deduplication.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `kit-core` | `io.github.dornol:file-kit-core` | Pure Java validation, upload/download/delete logic. No framework dependency. |
| `kit-spring-boot-starter` | `io.github.dornol:file-kit-spring-boot-starter` | Spring Boot auto-configuration with `@ValidMultipartFile`, WebFlux `FilePart` support, and storage integration. |

## Quick Start (Spring Boot)

### 1. Add dependency

```groovy
// Gradle
implementation 'io.github.dornol:file-kit-spring-boot-starter:0.1.8'

// Optional: for better MIME detection
implementation 'org.apache.tika:tika-core:3.1.0'

// Optional: for PDF metadata extraction
implementation 'org.apache.pdfbox:pdfbox:3.0.4'
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.dornol</groupId>
    <artifactId>file-kit-spring-boot-starter</artifactId>
    <version>0.1.8</version>
</dependency>
```

### 2. Define allowed media types

```java
public enum AllowedMediaType implements SafeMediaType {
    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    PNG("image/png", Set.of("png")),
    PDF("application/pdf", Set.of("pdf"));

    private final String mediaType;
    private final Set<String> extensions;

    AllowedMediaType(String mediaType, Set<String> extensions) {
        this.mediaType = mediaType;
        this.extensions = extensions;
    }

    @Override
    public String getMediaType() { return mediaType; }

    @Override
    public Set<String> getExtensions() { return extensions; }
}
```

### 3. Use the annotation

```java
@RestController
@Validated
public class FileUploadController {

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file")
            @ValidMultipartFile(value = AllowedMediaType.class, maxSize = 10 * 1024 * 1024)
            MultipartFile file) {
        // file is validated - safe to process
        return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
    }
}
```

That's it. No configuration class needed.

#### Image dimension validation

For image uploads, you can enforce minimum/maximum width and height:

```java
@PostMapping("/upload-avatar")
public ResponseEntity<?> uploadAvatar(
        @RequestParam("file")
        @ValidMultipartFile(
                value = AllowedMediaType.class,
                maxSize = 5 * 1024 * 1024,
                minWidth = 200, minHeight = 200,
                maxWidth = 4096, maxHeight = 4096)
        MultipartFile file) {
    // file is validated - guaranteed to be 200x200 ~ 4096x4096
    return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
}
```

Dimension constraints use ImageIO to read only the image header — no full decoding, minimal overhead. Non-image files fail validation when any dimension constraint is set.

### WebFlux (FilePart) Support

For Spring WebFlux applications, use `FilePartSource` to wrap a `FilePart` as a `FileSource`. This lets you use the same `FileUploadService` without any changes:

```java
@RestController
public class FileController {

    private final FileUploadService uploadService;

    @PostMapping("/upload")
    public Mono<Map<String, String>> upload(@RequestPart("file") FilePart filePart) {
        return FilePartSource.from(filePart)
            .flatMap(source -> Mono.fromCallable(() -> {
                try (source) {
                    FileMetadata metadata = uploadService.upload(
                        source, StorageType.LOCAL, "bucket");
                    return Map.of("fileKey", metadata.key());
                }
            }).subscribeOn(Schedulers.boundedElastic()));
    }
}
```

**How it works:**
- `FilePartSource.from(FilePart)` buffers the reactive `FilePart` content to a temporary file via `transferTo(Path)`
- The resulting `FilePartSource` implements `FileSource`, so it works with `FileUploadService`, `FileValidationHelper`, and all existing validation logic
- `getInputStream()` can be called multiple times (replayable), unlike `FilePart.content()` which is single-use
- Implements `Closeable` — always use try-with-resources to clean up the temporary file
- Use `Schedulers.boundedElastic()` for the blocking `FileUploadService` call inside a reactive pipeline

**Note:** `@ValidMultipartFile` is not available for `FilePart` because `FilePart.content()` is a single-use `Flux<DataBuffer>` — consuming it in a validator would prevent the controller from reading the file. Use `FileUploadService` for validation instead, which performs file size, filename, and virus scan checks.

### 4. Configuration

Configure file-kit via `application.yml`:

```yaml
file-kit:
  max-upload-size: 10485760  # 10MB, 0 = unlimited (default)
```

## File Storage

file-kit provides a pluggable storage abstraction. Implement the SPI interfaces and register a `FileStorage` bean, and the upload/download/delete flow is auto-configured.

### What you need to provide

| Interface | Description | Default |
|-----------|-------------|---------|
| `ChecksumCalculator` | Computes content checksum for deduplication | `Sha256ChecksumCalculator` (auto-registered) |
| `FileFormatExtractor` | Detects MIME type and extension from content | None — you must provide |
| `FileMetadataRepository` | Persists and queries file metadata | None — you must provide |
| `FileStorage` | Stores and loads file content | Built-in: `LocalFileStorage`, `InMemoryFileStorage` |
| `QuotaPolicy` | Defines maximum bytes per storage type/bucket | None — optional |
| `QuotaUsageProvider` | Reports current bytes used per storage type/bucket | None — optional |
| `FileEventListener` | Receives file lifecycle events | None — optional |

### Minimal setup

`ChecksumCalculator` is auto-registered (SHA-256). You only need to provide `FileFormatExtractor`, `FileMetadataRepository`, and at least one `FileStorage`:

```java
@Component
public class MyFileFormatExtractor implements FileFormatExtractor {
    public FileFormat extract(InputStream inputStream) { /* detect MIME & extension */ }
}

@Component
public class MyFileMetadataRepository implements FileMetadataRepository {
    public FileMetadata findByChecksum(String checksum) { /* query DB */ }
    public FileMetadata findByKey(String key) { /* query DB */ }
    public FileMetadata save(FileMetadata metadata) { /* insert DB */ }
    public void deleteByKey(String key) { /* delete from DB */ }
    // getByKey(key) is provided as a default method — throws FileStorageException if not found
}
```

### Built-in storage implementations

#### LocalFileStorage

Stores files on the local filesystem with configurable directory layout:

```java
public enum StorageType { LOCAL }

@Bean
public FileStorage localStorage() {
    return new LocalFileStorage(Path.of("/data/files"), StorageType.LOCAL);
}
```

Directory layout is controlled by `ObjectKeyStrategy`:

| Strategy | Example path | Use case |
|----------|-------------|----------|
| `ObjectKeyStrategy.flat()` | `key.png` | Simple, small-scale |
| `ObjectKeyStrategy.dateBased()` | `2026/03/15/key.png` | Date-based organization |
| `ObjectKeyStrategy.hashPrefixed(2)` | `73/3e/key.png` | Large-scale, avoids filesystem bottlenecks |

```java
@Bean
public FileStorage localStorage() {
    return new LocalFileStorage(
            Path.of("/data/files"),
            StorageType.LOCAL,
            ObjectKeyStrategy.hashPrefixed(2));
}
```

#### InMemoryFileStorage

Stores files in memory. Useful for testing and prototyping:

```java
@Bean
public FileStorage memoryStorage() {
    return new InMemoryFileStorage(StorageType.LOCAL);
}
```

### Custom storage (S3 example)

Implement `FileStorage` to integrate with any storage backend. Below is a full S3 implementation with error handling and pre-signed URL support.

#### 1. Add AWS SDK dependency

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

#### 2. Define storage type

```java
public enum StorageType { LOCAL, S3 }
```

#### 3. Implement FileStorage

`FileUploadCommand.content()` provides an `InputStream` for streaming — no need to load the entire file into memory:

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

#### 4. Configure S3Client and register the bean

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

> **S3-compatible services:** This implementation works with any S3-compatible storage — [MinIO](https://min.io/), [Cloudflare R2](https://developers.cloudflare.com/r2/), [Wasabi](https://wasabi.com/), etc. Just change the `endpoint` and credentials. For AWS S3, remove `endpointOverride()` and `forcePathStyle()`, and use the default credential provider chain instead of static credentials.

#### Implementing other backends (GCS, Azure Blob, etc.)

The same pattern applies to any storage backend — implement the five `FileStorage` methods and register as a Spring bean. The `example` module contains a working S3 implementation for reference.

### Multiple storage backends

Register multiple `FileStorage` beans to support different backends simultaneously. The `FileStorageResolver` routes to the correct one based on `storageType`. Each storage must have a unique `storageType` — duplicate types cause an `IllegalArgumentException` at startup:

```java
public enum StorageType { LOCAL, S3 }

@Bean
public FileStorage localStorage() {
    return new LocalFileStorage(Path.of("/data/files"), StorageType.LOCAL);
}

@Bean
public FileStorage s3Storage() {
    return new S3FileStorage(s3Client); // your implementation
}
```

Upload to a specific storage:

```java
// Store on local disk
uploadService.upload(file, StorageType.LOCAL, "uploads");

// Store on S3
uploadService.upload(file, StorageType.S3, "my-s3-bucket");
```

Download and delete are automatic — `FileMetadata` records which storage was used:

```java
// Automatically reads from the correct storage
downloadService.download(fileKey);

// Deletes from the correct storage + removes metadata
deleteService.delete(fileKey);
```

### Pre-signed URL

Generate time-limited direct-access URLs for storage backends that support it (e.g. S3):

```java
// Generate a pre-signed URL valid for 1 hour
String url = downloadService.generatePresignedUrl(fileKey, Duration.ofHours(1));
```

The default `FileStorage.generatePresignedUrl()` throws `UnsupportedOperationException`. See the [S3 example above](#3-implement-filestorage) for a full implementation.

### Range request (206 Partial Content)

`FileResponseBuilder` supports HTTP Range requests for streaming and resumable downloads:

```java
@GetMapping("/files/{fileKey}/stream")
public ResponseEntity<Resource> stream(
        @PathVariable String fileKey,
        @RequestHeader(value = "Range", required = false) String rangeHeader) {
    DownloadResult result = downloadService.download(fileKey);
    return FileResponseBuilder.inline(result.metadata())
            .range(rangeHeader)
            .body(new InputStreamResource(result.content()));
}
```

Behavior:
- No Range header → `200 OK` with `Accept-Ranges: bytes`
- Valid Range → `206 Partial Content` with `Content-Range` header
- Invalid Range → `416 Range Not Satisfiable`

`FileStorage` also provides a default `loadRange(metadata, start, end)` method that skips to the start offset and wraps the stream with `BoundedInputStream` for efficient partial reads.

### Transactional upload with callback

Run business logic after upload — if it fails, the file is automatically deleted:

```java
uploadService.upload(file, StorageType.LOCAL, "uploads", metadata -> {
    businessService.process(metadata);  // throws → file rolled back
});
```

### File deletion

`FileDeleteService` coordinates deletion across storage and metadata:

```java
@Autowired FileDeleteService deleteService;

deleteService.delete(fileKey);
// 1. Looks up metadata by key (throws FileStorageException if not found)
// 2. Deletes the physical file from storage
// 3. Deletes the metadata record
```

### Virus scanning

file-kit supports optional virus scanning via the `VirusScanner` SPI. When a `VirusScanner` is registered, uploaded files are automatically scanned before storage. Both `byte[]` and `InputStream` overloads are supported:

```java
@Component
public class ClamAvVirusScanner implements VirusScanner {

    @Override
    public ScanResult scan(byte[] fileBytes) {
        try {
            boolean clean = clamAvClient.scan(fileBytes);
            return clean ? ScanResult.clean() : ScanResult.infected("Threat detected");
        } catch (Exception e) {
            return ScanResult.error("Scan service unavailable: " + e.getMessage());
        }
    }

    // Optional: override for streaming support
    @Override
    public ScanResult scan(InputStream inputStream) {
        // e.g., pipe directly to ClamAV INSTREAM command
    }
}
```

The scan result determines the upload outcome:

| Status | Behavior |
|--------|----------|
| `CLEAN` | Upload proceeds normally |
| `INFECTED` | Upload rejected with `FileStorageException` (`VIRUS_DETECTED`) |
| `ERROR` | Upload rejected with `FileStorageException` (`VIRUS_SCAN_ERROR`) — fail-closed by default |

To implement **fail-open** semantics (allow upload on scan error), return `ScanResult.clean()` from your `VirusScanner` implementation's error handling.

If no `VirusScanner` bean is registered, scanning is skipped entirely.

### Upload / download flow

**Upload:**
1. Validate file size against configured maximum
2. Validate filename safety (length, path traversal)
3. Buffer file content to a temporary file (memory-safe for large files)
4. Virus scan (if `VirusScanner` is registered) — reject if INFECTED or ERROR
5. Compute checksum (streaming); if a file with the same checksum already exists, return the existing metadata (deduplication — **no quota consumed, no event fired**)
6. Quota check (if `QuotaChecker` is configured) — reject with `QUOTA_EXCEEDED` if over limit
7. Detect file format (MIME type, extension)
8. Delegate to `FileStorage.upload()` with an `InputStream` for streaming storage
9. Run callback (if provided) — on failure, delete from storage and throw
10. Save `FileMetadata` and fire `onUploaded` event
11. Clean up temporary file

**Download:**
1. Look up `FileMetadata` by file key
2. Resolve the correct `FileStorage` from `metadata.location().storageType()`
3. Load file content and fire `onDownloaded` event

**Delete:**
1. Look up `FileMetadata` by file key
2. Resolve the correct `FileStorage` and delete the physical file
3. Delete the metadata record and fire `onDeleted` event

### FileResponseBuilder

Utility for building download/inline HTTP responses with proper Content-Disposition (RFC 5987, Korean filename support) and Range request handling:

```java
// Download
return FileResponseBuilder.download(metadata).body(resource);

// Inline preview with cache
return FileResponseBuilder.inline(metadata)
        .cache(Duration.ofHours(1))
        .body(resource);

// Custom filename + content type
return FileResponseBuilder.download("report.xlsx")
        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .body(resource);

// Range request support (206 Partial Content)
return FileResponseBuilder.inline(metadata)
        .range(request.getHeader("Range"))
        .body(resource);
```

### Controller example (Spring MVC)

```java
@RestController
public class FileController {

    private final FileUploadService uploadService;
    private final FileDownloadService downloadService;
    private final FileDeleteService deleteService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam MultipartFile file) throws IOException {
        FileMetadata metadata = uploadService.upload(
                new MultipartFileSource(file), StorageType.LOCAL, "my-bucket");
        return ResponseEntity.ok(Map.of("fileKey", metadata.key()));
    }

    @GetMapping("/files/{fileKey}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileKey) {
        DownloadResult result = downloadService.download(fileKey);
        return FileResponseBuilder.download(result.metadata())
                .body(new InputStreamResource(result.content()));
    }

    @DeleteMapping("/files/{fileKey}")
    public ResponseEntity<?> delete(@PathVariable String fileKey) {
        deleteService.delete(fileKey);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/files/{fileKey}/presigned-url")
    public ResponseEntity<?> presignedUrl(@PathVariable String fileKey) {
        String url = downloadService.generatePresignedUrl(fileKey, Duration.ofHours(1));
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/files/{fileKey}/stream")
    public ResponseEntity<Resource> stream(
            @PathVariable String fileKey,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        DownloadResult result = downloadService.download(fileKey);
        return FileResponseBuilder.inline(result.metadata())
                .range(rangeHeader)
                .body(new InputStreamResource(result.content()));
    }
}
```

### Controller example (Spring WebFlux)

```java
@RestController
public class FileController {

    private final FileUploadService uploadService;

    @PostMapping("/upload")
    public Mono<Map<String, String>> upload(@RequestPart("file") FilePart filePart) {
        return FilePartSource.from(filePart)
            .flatMap(source -> Mono.fromCallable(() -> {
                try (source) {
                    FileMetadata metadata = uploadService.upload(
                        source, StorageType.LOCAL, "my-bucket");
                    return Map.of("fileKey", metadata.key());
                }
            }).subscribeOn(Schedulers.boundedElastic()));
    }
}
```

### Auto-configured beans

The following beans are registered automatically when their dependencies are present:

| Bean | Condition |
|------|-----------|
| `ChecksumCalculator` | Always (SHA-256 default, overridable) |
| `FileStorageResolver` | At least one `FileStorage` bean |
| `FileUploadService` | `FileMetadataRepository` + `FileFormatExtractor` + `FileStorageResolver` (+ optional `VirusScanner`) |
| `FileDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |
| `FileDeleteService` | `FileMetadataRepository` + `FileStorageResolver` |
| `SpringDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |
| `ImageMetadataExtractor` | Always (`ImageIOMetadataExtractor` default, overridable) |
| `ImageResizer` | Always (`ImageIOResizer` default, overridable) |
| `ImageWatermarker` | Always (`ImageIOWatermarker` default, overridable) |
| `ThumbnailGenerator` | Always (`DefaultThumbnailGenerator` default, overridable) |
| `PdfMetadataExtractor` | When Apache PDFBox is on the classpath (`PdfBoxMetadataExtractor` default, overridable) |
| `ArchiveMetadataExtractor` | Always (`ZipArchiveMetadataExtractor` default, overridable) |
| `ExifStripper` | Always (`ImageIOExifStripper` default, overridable) |
| `ImageFormatConverter` | Always (`ImageIOFormatConverter` default, overridable) |
| `FileTransferService` | `FileMetadataRepository` + `FileStorageResolver` |
| `FileRenameService` | `FileMetadataRepository` |
| `FileEncryptor` | Always (`NoOpFileEncryptor` default, overridable) |
| `QuotaChecker` | `QuotaPolicy` + `QuotaUsageProvider` (both required) |
| `FileEventPublisher` | Always (collects all `FileEventListener` beans, empty list if none) |
| `FileKitMetrics` | When Micrometer is on the classpath + `MeterRegistry` bean available |

### Micrometer metrics

When `spring-boot-starter-actuator` is on the classpath, file-kit automatically records metrics via `FileKitMetrics`:

| Metric | Type | Description |
|--------|------|-------------|
| `file.kit.uploads` | Counter | Upload count (tags: `storageType`, `bucket`) |
| `file.kit.upload.size` | DistributionSummary | Uploaded file size in bytes |
| `file.kit.downloads` | Counter | Download count |
| `file.kit.deletes` | Counter | Delete count |
| `file.kit.copies` | Counter | Copy count |
| `file.kit.moves` | Counter | Move count |

No configuration needed — just add the actuator dependency:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

Metrics are automatically exported to any configured Micrometer backend (Prometheus, Datadog, CloudWatch, etc.).

> **Cardinality warning:** Metrics are tagged by `storageType` and `bucket`. If your application uses a large number of distinct bucket names (e.g. per-user buckets), this can lead to high cardinality in your metrics backend. Consider using a fixed set of bucket names to keep metric cardinality manageable.

## Image Processing

file-kit provides image metadata extraction, resizing, thumbnail generation, watermarking, EXIF stripping, and format conversion as standalone utilities. These are **not** integrated into the upload flow — use them directly where needed.

### Metadata extraction

```java
@Autowired ImageMetadataExtractor metadataExtractor;

byte[] imageBytes = Files.readAllBytes(Path.of("photo.jpg"));
ImageMetadata metadata = metadataExtractor.extract(imageBytes);
// metadata.width(), metadata.height(), metadata.format()
```

### Image resizing

```java
@Autowired ImageResizer resizer;

byte[] imageBytes = Files.readAllBytes(Path.of("photo.jpg"));

// Thumbnail (fit within 128x128, preserving aspect ratio)
ResizeResult thumbnail = resizer.resize(imageBytes, ResizeOption.thumbnail(128));

// Fit within bounds (aspect ratio preserved)
ResizeResult fitted = resizer.resize(imageBytes, ResizeOption.fit(800, 600));

// Cover target area (crop to fill, aspect ratio preserved)
ResizeResult covered = resizer.resize(imageBytes, ResizeOption.cover(400, 400));

// Exact dimensions (stretches to fit)
ResizeResult exact = resizer.resize(imageBytes, ResizeOption.exact(1920, 1080));

// Custom: format conversion + quality
ResizeOption option = new ResizeOption(800, 600, ScaleMode.FIT, "jpeg", 0.9f);
ResizeResult converted = resizer.resize(imageBytes, option);

// Note: width/height must be positive, quality must be 0.0-1.0
// Invalid values throw IllegalArgumentException
```

### Scale modes

| Mode | Behavior |
|------|----------|
| `FIT` | Scale to fit within target dimensions, preserving aspect ratio. Result may be smaller than target. |
| `COVER` | Scale to cover target dimensions, preserving aspect ratio. Result is cropped to exact target size. |
| `EXACT` | Scale to exact target dimensions, ignoring aspect ratio. |

`ResizeOption` validates its parameters at construction: `targetWidth` and `targetHeight` must be positive, and `quality` must be between 0.0 and 1.0 (inclusive).

### Thumbnail generation

`ThumbnailGenerator` provides a simplified API for generating thumbnails:

```java
@Autowired ThumbnailGenerator thumbnailGenerator;

byte[] imageBytes = Files.readAllBytes(Path.of("photo.jpg"));

// Default thumbnail (200px max dimension, 0.8 quality)
ResizeResult thumbnail = thumbnailGenerator.generate(imageBytes, ThumbnailOption.defaults());

// Custom size
ResizeResult small = thumbnailGenerator.generate(imageBytes, ThumbnailOption.ofSize(128));

// Custom size + format + quality
ThumbnailOption option = new ThumbnailOption(256, "jpeg", 0.9f);
ResizeResult custom = thumbnailGenerator.generate(imageBytes, option);
```

The default `DefaultThumbnailGenerator` delegates to `ImageResizer` with `ScaleMode.FIT`, preserving aspect ratio.

### Watermark

`ImageWatermarker` applies text or image watermarks to images:

```java
@Autowired ImageWatermarker watermarker;

byte[] imageBytes = Files.readAllBytes(Path.of("photo.jpg"));

// Text watermark (center, 50% opacity)
WatermarkOption textOption = WatermarkOption.text("© 2026 ACME", WatermarkPosition.CENTER, 0.5f);
WatermarkResult result = watermarker.apply(imageBytes, textOption);
// result.data() → watermarked image bytes
// result.metadata() → width, height, format

// Image watermark (logo overlay, bottom-right corner)
byte[] logo = Files.readAllBytes(Path.of("logo.png"));
WatermarkOption logoOption = WatermarkOption.image(logo, WatermarkPosition.BOTTOM_RIGHT, 0.7f);
WatermarkResult logoResult = watermarker.apply(imageBytes, logoOption);

// Tiled watermark (repeated across entire image)
WatermarkOption tiledOption = WatermarkOption.text("DRAFT", WatermarkPosition.TILED, 0.3f);
WatermarkResult tiledResult = watermarker.apply(imageBytes, tiledOption);

// Full control: custom font, font size, output format, quality
WatermarkOption custom = new WatermarkOption(
        WatermarkOption.WatermarkType.TEXT, "Confidential", null,
        WatermarkPosition.CENTER, 0.5f, "Serif", 48, "jpeg", 0.9f);
WatermarkResult customResult = watermarker.apply(imageBytes, custom);
```

**Watermark positions:**

| Position | Behavior |
|----------|----------|
| `CENTER` | Centered on the image |
| `TOP_LEFT` | Top-left corner with padding |
| `TOP_RIGHT` | Top-right corner with padding |
| `BOTTOM_LEFT` | Bottom-left corner with padding |
| `BOTTOM_RIGHT` | Bottom-right corner with padding |
| `TILED` | Repeated across the entire image |

### EXIF stripping

Strip EXIF and other metadata from images by re-encoding through ImageIO:

```java
@Autowired ExifStripper exifStripper;

byte[] imageBytes = Files.readAllBytes(Path.of("photo.jpg"));

// Strip with default quality (0.95)
byte[] stripped = exifStripper.strip(imageBytes);

// Strip with custom quality
byte[] strippedLow = exifStripper.strip(imageBytes, 0.8f);
```

### Image format conversion

Convert images between formats (e.g., PNG → JPEG) without resizing:

```java
@Autowired ImageFormatConverter converter;

byte[] pngBytes = Files.readAllBytes(Path.of("image.png"));

// Convert to JPEG with default quality
ConvertResult result = converter.convert(pngBytes, ConvertOption.of("jpeg"));
// result.data() → converted image bytes
// result.metadata() → width, height, format

// Convert with custom quality
ConvertResult hq = converter.convert(pngBytes, ConvertOption.of("jpeg", 0.95f));
```

### Custom implementation

`ImageMetadataExtractor`, `ImageResizer`, `ImageWatermarker`, `ThumbnailGenerator`, `ExifStripper`, and `ImageFormatConverter` are all SPI interfaces with default `ImageIO`-based implementations. Register your own bean to override:

```java
@Bean
public ImageResizer imageResizer() {
    return new ThumbnailatorResizer(); // e.g., using Thumbnailator library
}

@Bean
public ImageWatermarker imageWatermarker() {
    return new MyCustomWatermarker(); // e.g., using external library
}
```

## PDF Metadata Extraction

Extract metadata from PDF documents using Apache PDFBox. This feature is auto-configured when PDFBox is on the classpath.

### Add PDFBox dependency

```groovy
implementation 'org.apache.pdfbox:pdfbox:3.0.4'
```

### Usage

```java
@Autowired PdfMetadataExtractor pdfExtractor;

byte[] pdfBytes = Files.readAllBytes(Path.of("document.pdf"));
PdfMetadata metadata = pdfExtractor.extract(pdfBytes);

metadata.pageCount();    // number of pages
metadata.title();        // document title (nullable)
metadata.author();       // document author (nullable)
metadata.creator();      // creator application (nullable)
metadata.creationDate(); // creation date as Instant (nullable)

// Also works with InputStream
try (InputStream is = Files.newInputStream(Path.of("document.pdf"))) {
    PdfMetadata meta = pdfExtractor.extract(is);
}
```

### Custom implementation

`PdfMetadataExtractor` is an SPI interface. Register your own bean to override:

```java
@Bean
public PdfMetadataExtractor pdfMetadataExtractor() {
    return new MyCustomPdfExtractor(); // e.g., using iText or other library
}
```

## ZIP Archive Listing

Extract metadata from ZIP archives without fully extracting files:

```java
@Autowired ArchiveMetadataExtractor archiveExtractor;

byte[] zipBytes = Files.readAllBytes(Path.of("archive.zip"));
ArchiveMetadata metadata = archiveExtractor.extract(zipBytes);

metadata.entryCount();            // number of entries
metadata.totalUncompressedSize(); // total uncompressed size in bytes

for (ArchiveEntry entry : metadata.entries()) {
    entry.path();             // entry path within archive
    entry.compressedSize();   // compressed size in bytes
    entry.uncompressedSize(); // uncompressed size in bytes
    entry.lastModified();     // last modification time (nullable)
    entry.directory();        // whether this is a directory entry
}

// Also works with InputStream
try (InputStream is = Files.newInputStream(Path.of("archive.zip"))) {
    ArchiveMetadata meta = archiveExtractor.extract(is);
}
```

The default `ZipArchiveMetadataExtractor` uses `java.util.zip.ZipInputStream` — no external dependencies required. It includes built-in zip bomb protection with configurable limits:

```java
// Default limits: 1 GB max uncompressed size, 65,535 max entries
ArchiveMetadataExtractor extractor = new ZipArchiveMetadataExtractor();

// Custom limits
ArchiveMetadataExtractor strict = new ZipArchiveMetadataExtractor(
        100 * 1024 * 1024,  // 100 MB max uncompressed size
        1000);               // max 1,000 entries
```

Archives exceeding either limit throw `FileStorageException` with `ARCHIVE_PROCESSING_FAILED`.

## File Copy/Move

Copy or move files between storage backends:

```java
@Autowired FileTransferService transferService;

// Copy a file to a different bucket (or storage backend)
FileMetadata copied = transferService.copy(fileKey, StorageType.S3, "archive-bucket");
// Source file remains, new file gets a new UUID key

// Move a file (copy + delete source)
FileMetadata moved = transferService.move(fileKey, StorageType.S3, "archive-bucket");
// Source file and metadata are deleted after successful copy
```

Copy preserves the original filename, checksum, and format while assigning a new UUID key and storage location. Move performs a copy followed by source deletion — if source deletion fails after a successful copy, a `FileStorageException` with `MOVE_FAILED` is thrown.

When a `QuotaChecker` is configured, both `copy()` and `move()` check the target bucket's quota before proceeding. Copy fires an `onCopied` event; move fires an `onMoved` event (never `onCopied`).

### Batch copy/move

Copy or move multiple files at once with best-effort strategy:

```java
@Autowired FileTransferService transferService;

// Copy multiple files to another backend
BatchTransferResult copyResult = transferService.copyAll(
        List.of("key1", "key2"), StorageType.S3, "archive-bucket");

// Move multiple files
BatchTransferResult moveResult = transferService.moveAll(
        List.of("key1", "key2"), StorageType.S3, "archive-bucket");

copyResult.succeeded();      // List<FileMetadata> of new copies
copyResult.failed();         // Map<String, String> of source keys to error messages
copyResult.allSucceeded();   // true if no failures
copyResult.totalRequested(); // total count requested
```

## Batch Upload

Upload multiple files at once with best-effort strategy:

```java
@Autowired FileUploadService uploadService;

BatchUploadResult result = uploadService.uploadAll(fileSources, StorageType.LOCAL, "uploads");

result.succeeded();      // List<FileMetadata> of uploaded files
result.failed();         // Map<String, String> of filenames to error messages
result.allSucceeded();   // true if no failures
result.totalRequested(); // total count requested
```

Deduplication applies per file — if two files have the same content, the existing metadata is returned for both.

## Batch Delete

Delete multiple files at once with best-effort strategy:

```java
@Autowired FileDeleteService deleteService;

BatchDeleteResult result = deleteService.deleteAll(List.of("key1", "key2", "key3"));

result.succeeded();      // List of successfully deleted keys
result.failed();         // Map of failed keys to error messages
result.allSucceeded();   // true if no failures
result.totalRequested(); // total count requested
```

The method attempts every deletion and collects results — it does not stop on first failure. This is intentional because file storage operations are not transactional.

## Storage Quota

file-kit supports optional quota enforcement via two SPI interfaces. When both are registered, uploads and copy/move operations are checked against the quota before proceeding.

### Implement the SPI

```java
@Component
public class MyQuotaPolicy implements QuotaPolicy {
    @Override
    public long getMaxBytes(Enum<?> storageType, String bucket) {
        // e.g., look up per-tenant limit from config or DB
        return 100 * 1024 * 1024; // 100 MB
    }
}

@Component
public class MyQuotaUsageProvider implements QuotaUsageProvider {
    @Override
    public long getUsedBytes(Enum<?> storageType, String bucket) {
        // e.g., query DB for current usage
        return fileRepository.sumSizeByBucket(bucket);
    }
}
```

When both beans are registered, `QuotaChecker` is auto-configured and injected into `FileUploadService` and `FileTransferService`. No additional configuration needed.

### Behavior

- **Upload**: quota is checked after deduplication (dedup hits don't consume quota)
- **Copy/Move**: quota is checked against the target bucket before the copy
- **Quota exceeded**: throws `FileStorageException` with `QUOTA_EXCEEDED`
- If neither `QuotaPolicy` nor `QuotaUsageProvider` is registered, quota checking is skipped entirely

### Programmatic usage

```java
QuotaChecker checker = new QuotaChecker(policy, usageProvider);

// Check before custom operations
checker.check(StorageType.S3, "my-bucket", fileSize);

// Query current usage
QuotaUsage usage = checker.getUsage(StorageType.S3, "my-bucket");
usage.usedBytes();      // current usage
usage.maxBytes();       // configured limit
usage.remainingBytes(); // available space
```

## File Existence Check

Check whether a file exists without downloading it:

```java
@Autowired FileMetadataRepository metadataRepository;

boolean exists = metadataRepository.existsByKey(fileKey);
```

This is a default method on the `FileMetadataRepository` SPI — no additional implementation required. Useful for pre-upload dedup checks or conditional logic.

## File Rename

Rename a file by updating its metadata without touching storage:

```java
@Autowired FileRenameService renameService;

FileMetadata renamed = renameService.rename(fileKey, "new-name.txt");
// Only metadata changes — storage is not affected
// Fires onRenamed event
```

The same filename validation rules apply (path traversal, length limit). The rename fires an `onRenamed` event to all registered `FileEventListener`s.

## File Lifecycle Events

file-kit publishes events for file operations via the `FileEventListener` SPI. Use this for audit logging, cache invalidation, statistics, or notifications.

### Implement a listener

```java
@Component
public class AuditFileEventListener implements FileEventListener {

    @Override
    public void onUploaded(FileMetadata metadata) {
        auditLog.record("FILE_UPLOADED", metadata.key(), metadata.size());
    }

    @Override
    public void onDeleted(FileMetadata metadata) {
        auditLog.record("FILE_DELETED", metadata.key());
    }

    // Override only the methods you need — all have default no-op implementations
}
```

### Events

| Event | Fired when | Parameters |
|-------|-----------|------------|
| `onUploaded` | After metadata is saved | uploaded metadata |
| `onDownloaded` | After content is loaded | downloaded metadata |
| `onDeleted` | After storage + metadata deletion | deleted metadata |
| `onCopied` | After copy completes | source + copy metadata |
| `onMoved` | After move completes (copy + source deletion) | source + moved metadata |
| `onRenamed` | After metadata name update | before + after metadata |

### Behavior

- **Fire-and-forget**: listener exceptions are logged and swallowed — a failing listener never breaks the file operation
- **Multiple listeners**: all registered `FileEventListener` beans are invoked in order
- **Deduplication**: dedup hits do not fire `onUploaded`
- **Move vs Copy**: `move()` fires only `onMoved`, never `onCopied`
- If no listeners are registered, `FileEventPublisher` is still created (with an empty list) — zero overhead

## Encryption at Rest

file-kit supports optional at-rest encryption via the `FileEncryptor` SPI. When a custom `FileEncryptor` is registered, files are automatically encrypted before storage and decrypted on download.

```java
@Component
public class AesFileEncryptor implements FileEncryptor {

    private final SecretKey key;

    @Override
    public void encrypt(InputStream plainInput, OutputStream cipherOutput) throws IOException {
        // Use CipherOutputStream with AES
    }

    @Override
    public void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException {
        // Use CipherInputStream with AES
    }
}
```

**How it works:**
- Upload: checksum is computed on **plaintext** (preserving deduplication), then content is encrypted to a temp file before storage
- Download: encrypted content is loaded from storage, then decrypted before returning to the caller
- The `NoOpFileEncryptor` default performs no encryption (pass-through)
- `FileMetadata.size` stores the **original** plaintext size; the encrypted size is used only for `FileUploadCommand.contentLength`

If no custom `FileEncryptor` bean is registered, encryption is skipped entirely.

## Validation Checks

| Check | Description |
|-------|-------------|
| Media type | Detects actual MIME type and compares against allowed set |
| File empty | Rejects zero-byte files |
| File size | Rejects files exceeding `maxSize` (in bytes) |
| Filename | Rejects null, blank, too-long (>200 chars), and path traversal patterns (`..`, `/`, `\`) |
| Extension | Verifies that file extension matches the detected content type |
| Image dimensions | Validates width/height against `minWidth`/`maxWidth`/`minHeight`/`maxHeight` (only when set) |

## Error Messages

### Validation messages

file-kit uses Jakarta Validation's standard message interpolation. Add a `ValidationMessages.properties` to your classpath:

```properties
file-kit.validation.unsupported-media-type=Unsupported file type
file-kit.validation.file-empty=File is empty
file-kit.validation.file-too-large=File size exceeded
file-kit.validation.invalid-filename=Invalid filename
file-kit.validation.invalid-extension=Invalid file extension
file-kit.validation.image-not-readable=File is not a valid image
file-kit.validation.image-width-too-small=Image width is too small
file-kit.validation.image-width-too-large=Image width is too large
file-kit.validation.image-height-too-small=Image height is too small
file-kit.validation.image-height-too-large=Image height is too large
```

### Storage error messages

Storage operations throw `FileStorageException` with a `messageKey` for i18n:

```properties
file-kit.storage.file-not-found=File not found
file-kit.storage.storage-not-found=Unregistered storage type
file-kit.storage.upload-failed=File upload failed
file-kit.storage.download-failed=File download failed
file-kit.storage.delete-failed=File deletion failed
file-kit.storage.callback-failed=Post-upload processing failed, file has been deleted
file-kit.storage.file-too-large=File size exceeds the maximum allowed
file-kit.storage.invalid-filename=Invalid filename
file-kit.storage.virus-detected=Virus detected in uploaded file
file-kit.storage.virus-scan-error=Virus scan failed
file-kit.storage.presigned-url-failed=Pre-signed URL generation failed
file-kit.storage.range-not-satisfiable=Invalid byte range requested
file-kit.image.processing-failed=Image processing failed
file-kit.pdf.processing-failed=PDF processing failed
file-kit.archive.processing-failed=Archive processing failed
file-kit.storage.copy-failed=File copy failed
file-kit.storage.move-failed=File move failed
file-kit.storage.encryption-failed=File encryption failed
file-kit.storage.decryption-failed=File decryption failed
file-kit.storage.quota-exceeded=Storage quota exceeded
```

Use `exception.getMessageKey()` to look up the localized message in your application.

## Media Type Detection

The starter auto-registers a `MediaTypeDetector` with the following priority:

| Priority | Condition | Detector |
|----------|-----------|----------|
| 1 | User-defined `MediaTypeDetector` bean | Your implementation |
| 2 | Apache Tika on classpath | `TikaMediaTypeDetector` |
| 3 | Fallback | `DefaultMediaTypeDetector` (Java `URLConnection`-based) |

For production use, Apache Tika is recommended for accurate detection.

## Using without Spring Boot

Use `kit-core` directly with the `FileSource` abstraction:

```java
MediaTypeDetector detector = new DefaultMediaTypeDetector();
FileValidationHelper helper = new FileValidationHelper(detector);

// Wrap your file as a FileSource
FileSource source = new FileSource() {
    public String getOriginalFilename() { return "photo.jpg"; }
    public InputStream getInputStream() { return ...; }
    public long getSize() { return 1024; }
    public boolean isEmpty() { return false; }
};

// Use helper methods directly
boolean validType = helper.isValidMediaType(source, allowedTypes);
boolean validName = helper.isValidFilename(source);
```

Or use `@ValidFile` with Jakarta Validation and `FileSourceValidator`.

Image processing, thumbnail, watermark, PDF metadata extraction, EXIF stripping, format conversion, and archive listing can also be used standalone without Spring:

```java
ImageResizer resizer = new ImageIOResizer();
ImageWatermarker watermarker = new ImageIOWatermarker();
ThumbnailGenerator thumbnailGenerator = new DefaultThumbnailGenerator(resizer);
PdfMetadataExtractor pdfExtractor = new PdfBoxMetadataExtractor();
ExifStripper exifStripper = new ImageIOExifStripper();
ImageFormatConverter formatConverter = new ImageIOFormatConverter();
ArchiveMetadataExtractor archiveExtractor = new ZipArchiveMetadataExtractor();
FileEncryptor encryptor = new NoOpFileEncryptor(); // or your custom implementation
```

## Running the Example

The `example` module is a full Spring Boot app demonstrating how to wire up all the SPI implementations:

| SPI Interface | Example Implementation | Notes |
|---------------|----------------------|-------|
| `FileStorage` | `LocalFileStorage`, `S3FileStorage` | Both registered — dual storage |
| `FileMetadataRepository` | `FileMetadataRepositoryAdapter` | JPA/PostgreSQL-backed |
| `FileFormatExtractor` | `TikaFileFormatExtractor` | Apache Tika-based |

```bash
# Start PostgreSQL + MinIO
cd example
docker compose up -d

# Run the app
./gradlew :example:bootRun
```

Open `http://localhost:8880` — upload files to LOCAL or S3, view the file list, download, and delete.

MinIO console: `http://localhost:9001` (minioadmin / minioadmin)

### Example endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/upload` | POST | Upload a file (multipart) |
| `/files` | GET | List all uploaded files |
| `/files/{fileKey}/download` | GET | Download a file |
| `/files/{fileKey}/stream` | GET | Stream a file with Range header support |
| `/files/{fileKey}/presigned-url` | GET | Generate a pre-signed URL |
| `/files/{fileKey}/uri` | GET | Resolve file URI |
| `/files/{fileKey}` | DELETE | Delete a file |
| `/image/metadata` | POST | Extract image metadata |
| `/image/resize` | POST | Resize an image |
| `/image/thumbnail` | POST | Generate a thumbnail |
| `/image/watermark` | POST | Apply a text watermark |
| `/pdf/metadata` | POST | Extract PDF metadata |
| `/image/strip-exif` | POST | Strip EXIF metadata from an image |
| `/image/convert` | POST | Convert image format |
| `/archive/metadata` | POST | Extract ZIP archive metadata |
| `/files/{fileKey}/copy` | POST | Copy a file to a new location |
| `/files/{fileKey}/move` | POST | Move a file to a new location |
| `/files/batch` | DELETE | Batch delete multiple files |

## Security Considerations

### Input validation

All domain records (`FileFormat`, `FileLocation`, `FileMetadata`) and command objects (`FileUploadCommand`) validate their constructor parameters. Null values for required fields are rejected immediately with `NullPointerException`, and invalid values (e.g., negative file size) throw `IllegalArgumentException`. `FileUploadCommand.originalFilename` is explicitly `@Nullable` — when null, a generated name is used during upload.

### Streaming upload

The upload service buffers file content to a temporary file on disk, then streams from it for each processing step (virus scan, checksum, format detection, storage). This ensures that arbitrarily large files can be uploaded without loading the entire content into memory.

### Filename safety

The upload service validates filenames **before** reading file content:
- Maximum length: 200 characters
- Rejected patterns: `..` (path traversal), `/` and `\` (directory separator)
- Null filenames are allowed — a generated name (`{key}.{extension}`) is used

These checks are also performed by `@ValidFile` / `@ValidMultipartFile` annotation validators. The shared logic lives in `FilenameValidator` for consistency.

### Bucket name restrictions

Bucket names are validated to contain only alphanumeric characters, dots, hyphens, and underscores (`[a-zA-Z0-9._-]+`). The validation is shared via `BucketNameValidator` and applied consistently in both `FileLocation` and `FileUploadCommand`.

### File size limits

Configure a maximum upload size to prevent memory exhaustion:

```yaml
# application.yml
file-kit:
  max-upload-size: 52428800  # 50 MB, 0 = unlimited (default)
```

Or when using `kit-core` directly:

```java
FileUploadService.builder(checksumCalculator, metadataRepository,
        formatExtractor, storageResolver)
        .maxUploadSize(50 * 1024 * 1024)
        .build();
```

### Checksum deduplication and concurrency (TOCTOU)

The checksum-based deduplication (`findByChecksum` → `save`) is not atomic. Under concurrent uploads of the same file, duplicate entries may be stored. If strict uniqueness is required, enforce a unique constraint on the checksum column in your `FileMetadataRepository` implementation.

### Download authorization

file-kit does **not** handle download authorization. Access control (e.g., verifying that the requesting user owns the file) is the application's responsibility. Wrap `FileDownloadService` or `SpringDownloadService` calls with your own authorization logic.

### ZIP bomb protection

`ZipArchiveMetadataExtractor` enforces two limits to prevent zip bomb attacks:
- **Maximum total uncompressed size**: 1 GB (default). Rejects archives whose cumulative uncompressed content exceeds this limit.
- **Maximum entry count**: 65,535 (default). Rejects archives with more entries than this threshold.

Both limits are configurable via the constructor. When exceeded, a `FileStorageException` with `ARCHIVE_PROCESSING_FAILED` is thrown immediately, without processing the remaining entries.

### Resource management

All internal streams and temporary files are properly closed/deleted on both success and error paths. Specifically:
- Upload temp files are cleaned up in a `finally` block, even if callbacks or storage operations fail
- Decryption temp files are deleted if decryption fails (not just on stream close)
- `DeleteOnCloseInputStream` cleans up the temp file even if opening the stream fails in the constructor
- WebFlux `FilePartSource` cleans up temp files on `transferTo()` or `Files.size()` failure via `onErrorResume`
- Range request streams are closed if byte seeking fails
- Validation streams are closed after media type detection

### Thread safety

- `FileUploadService`, `FileDownloadService`, `FileDeleteService`, `FileTransferService`, and `LocalFileStorage` are thread-safe and can be used as singletons.
- `InMemoryFileStorage` is thread-safe (backed by `ConcurrentHashMap`) but is **not recommended for production** — it has no size limits and all data is lost on restart.

## Requirements

- Java 17+
- Jakarta Validation 3.x (for annotation-based validation)
- Spring Boot 3+ / 4+ (for the starter module)
- Apache PDFBox 3.x (optional, for PDF metadata extraction)

## License

[MIT](LICENSE)
