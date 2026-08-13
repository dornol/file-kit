# file-kit

A lightweight Java library for file validation, upload, download, and deletion. Validates uploaded files by media type, file size, filename safety, and extension-content consistency. Provides a pluggable storage abstraction for uploading and downloading files with checksum-based deduplication.

**Deep-dive documentation:** [docs/guide/](docs/guide/README.md), including the
[production operations checklist](docs/guide/production-operations.md). AI
agents: see [llms.txt](llms.txt).

## What's New in 0.2.3

Patch release focused on storage safety, Spring Boot integration, and operational visibility:

- Hardened filename, archive, URL, domain, and storage input validation.
- Added concurrent duplicate-upload protection and safer S3 example configuration.
- Added optional `StorageHealthCheck` support and Spring Boot 4 Actuator integration.
- Improved Spring upload and byte-range handling, with tests and operational documentation.
- Added a streaming `ClamAvVirusScanner` with bounded I/O, timeouts, and fail-closed errors.
- Added a production-oriented example profile with environment-driven infrastructure settings.
- Added opt-in local orphan cleanup guidance and hardened MVC-only auto-configuration.

## 0.2.0 Highlights

Fifteen focused PDCA cycles that close every outstanding item from the internal library review. Full entry list is in the [CHANGELOG](CHANGELOG.md); highlights:

- **Streaming checksum verification on download** — `ChecksumVerifyingInputStream` + `ChecksumComputation` SPI. Memory footprint is O(buffer) regardless of file size (previously O(file)). Enable with `FileDownloadService.Builder.checksumCalculator(...)`.
- **Upload pipeline I/O reduction** — single-pass tee ingest (write + checksum + header-buffer) cuts tempFile re-reads from 4 to 2 per upload.
- **`TempFileBuffer`** (`io/`) — `Closeable` temp-file helper for `try-with-resources` cleanup, with `release()` for ownership-transfer cases.
- **Configurable temp directory** — `tempDirectory(Path)` on Upload/Transfer/Download builders. Null default keeps system `java.io.tmpdir`; set for dedicated SSD/tmpfs mounts, Docker volumes, or per-test `@TempDir` isolation.
- **`onUploadFailed` event** — `FileEventListener.onUploadFailed(metadata, cause)` fires after storage cleanup on callback or save failures. Subscribe instead of catching `CALLBACK_FAILED` manually.
- **Async adapters** (new `async/` package) — `CompletableFuture`-returning wrappers around all five sync services. Injectable `Executor` (default `ForkJoinPool.commonPool()`); inject `Executors.newVirtualThreadPerTaskExecutor()` on JDK 21+ for blocking I/O. Parallel batch variants: `copyAllParallelAsync` / `moveAllParallelAsync` / `deleteAllParallelAsync`.
  ```java
  Mono.fromFuture(asyncUpload.uploadAsync(src, type, bucket))
      .subscribeOn(Schedulers.boundedElastic());
  ```
- **`ChecksumAlgorithm` enum** — `MessageDigestChecksumCalculator(ChecksumAlgorithm.MD5 | SHA_1 | SHA_256 | SHA_384 | SHA_512)`. `Sha256ChecksumCalculator` retained as a no-arg convenience subclass.
- **Magic-byte MIME fallback** — `DefaultMediaTypeDetector` runs a magic-byte sniff before JDK probes. Covers PDF, ZIP / DOCX / XLSX / PPTX / APK, PNG, JPEG, GIF, BMP, WebP, MP4, OGG, Zstandard.
- **`SignedUrlSigner`** (new `url/` package) — HMAC-SHA256 helper for time-limited local-storage download URLs. Constant-time comparison via `MessageDigest.isEqual`. Authorization remains the app's job.
- **Image rotate / crop** — `ImageRotator` (90°/180°/270° via `RotateAngle` enum) and `ImageCropper` (pixel region with boundary validation).
- **Batch failure aggregation** — `BatchUploadResult.failureReasons()` / `BatchTransferResult.failureReasons()` / `BatchDeleteResult.failureReasons()` return `Map<String, Integer>` of reason → count.
- **Validation helper split** — `MediaTypeValidator` and `ImageDimensionValidator` extracted as public final classes; `FileValidationHelper` retained as a backward-compatible facade.

**No breaking API changes** across any of the fifteen cycles. All additions are either new classes, new methods, or new default-method overloads with null-fallback defaults.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `kit-core` | `io.github.dornol:file-kit-core` | Pure Java validation, upload/download/delete logic. No framework dependency. |
| `kit-spring-boot-starter` | `io.github.dornol:file-kit-spring-boot-starter` | Spring Boot auto-configuration with `@ValidMultipartFile`, WebFlux `FilePart` support, and storage integration. |

## Quick Start (Spring Boot)

### 1. Add dependency

```groovy
// Gradle
implementation 'io.github.dornol:file-kit-spring-boot-starter:0.2.3'

// Optional: for better MIME detection
implementation 'org.apache.tika:tika-core:3.3.1'

// Optional: for PDF metadata extraction
implementation 'org.apache.pdfbox:pdfbox:3.0.7'
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.dornol</groupId>
    <artifactId>file-kit-spring-boot-starter</artifactId>
    <version>0.2.3</version>
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

    @Override public String getMediaType() { return mediaType; }
    @Override public Set<String> getExtensions() { return extensions; }
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

That's it. No configuration class needed. For image dimension constraints, WebFlux `FilePart` support, and `application.yml` options, see [docs/guide/file-upload.md](docs/guide/file-upload.md).

### 4. Configuration

```yaml
file-kit:
  max-upload-size: 10485760              # 10MB, 0 = unlimited (default)
  verify-checksum-on-download: false     # verify integrity on download (default: false)
  max-presigned-expiration: 24h          # maximum pre-signed URL lifetime (default: no limit)
  encryption-required: false             # fail startup without a custom FileEncryptor
  metrics-include-bucket: false          # include bucket in metric tags (default: false)
```

For a deployment-oriented configuration template, see
[`example/src/main/resources/application-prod.yaml`](example/src/main/resources/application-prod.yaml).
Activate it with `SPRING_PROFILES_ACTIVE=prod` and provide database/S3
credentials through environment variables. The template deliberately keeps
encryption mandatory and fails startup until a real `FileEncryptor` bean is
registered. Set `FILE_KIT_ENCRYPTION_REQUIRED=false` only for a deliberate
non-encrypted deployment.

## Features

| Feature | Key classes | Guide |
|---|---|---|
| `@ValidMultipartFile` annotation, WebFlux `FilePart` | `@ValidMultipartFile`, `FilePartSource` | [file-upload.md](docs/guide/file-upload.md) |
| Upload / download / delete services | `FileUploadService`, `FileDownloadService`, `FileDeleteService`, `FileResponseBuilder` | [download.md](docs/guide/download.md) |
| Pluggable storage SPI | `FileStorage`, `LocalFileStorage`, `InMemoryFileStorage`, `ObjectKeyStrategy` | [storage-spi.md](docs/guide/storage-spi.md) |
| S3 / MinIO / R2 reference implementation | `S3FileStorage` (example) | [s3-storage.md](docs/guide/s3-storage.md) |
| Batch upload / delete, copy / move, ZIP listing, rename | `FileTransferService`, `FileRenameService`, `ArchiveMetadataExtractor` | [batch-operations.md](docs/guide/batch-operations.md) |
| Storage quota | `QuotaPolicy`, `QuotaUsageProvider`, `QuotaChecker` | [quota.md](docs/guide/quota.md) |
| At-rest encryption | `FileEncryptor` | [encryption.md](docs/guide/encryption.md) |
| Lifecycle events | `FileEventListener`, `FileEventPublisher` | [lifecycle-events.md](docs/guide/lifecycle-events.md) |
| Validation rules + i18n error keys | `FileValidationHelper`, `MediaTypeValidator`, `ImageDimensionValidator`, `FileStorageException` | [validation-and-errors.md](docs/guide/validation-and-errors.md) |
| Image metadata / resize / thumbnail / watermark / rotate / crop / EXIF strip / format convert | `ImageMetadataExtractor`, `ImageResizer`, `ThumbnailGenerator`, `ImageWatermarker`, `ImageRotator`, `ImageCropper`, `ExifStripper`, `ImageFormatConverter` | [image-processing.md](docs/guide/image-processing.md) |
| PDF metadata | `PdfMetadataExtractor`, `PdfBoxMetadataExtractor` | [pdf-metadata.md](docs/guide/pdf-metadata.md) |
| Async adapters, signed URLs, standalone use | `AsyncFile*Service`, `SignedUrlSigner`, `TempFileBuffer` | [standalone.md](docs/guide/standalone.md) |

## Auto-configured beans

Registered by `kit-spring-boot-starter` when the listed conditions are met. All are `@ConditionalOnMissingBean` — provide your own `@Bean` to override.

| Bean | Condition |
|------|-----------|
| `ChecksumCalculator` | Always (SHA-256 default) |
| `FileStorageResolver` | At least one `FileStorage` bean |
| `FileUploadService` | `FileMetadataRepository` + `FileFormatExtractor` + `FileStorageResolver` (+ optional `VirusScanner`) |
| `FileDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |
| `FileDeleteService` | `FileMetadataRepository` + `FileStorageResolver` |
| `FileTransferService` | `FileMetadataRepository` + `FileStorageResolver` |
| `FileRenameService` | `FileMetadataRepository` |
| `SpringDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |
| `ImageMetadataExtractor` | Always (`ImageIOMetadataExtractor` default) |
| `ImageResizer` | Always (`ImageIOResizer` default) |
| `ImageWatermarker` | Always (`ImageIOWatermarker` default) |
| `ImageRotator` | Always (`ImageIORotator` default) |
| `ImageCropper` | Always (`ImageIOCropper` default) |
| `ThumbnailGenerator` | Always (`DefaultThumbnailGenerator` default) |
| `ExifStripper` | Always (`ImageIOExifStripper` default) |
| `ImageFormatConverter` | Always (`ImageIOFormatConverter` default) |
| `ArchiveMetadataExtractor` | Always (`ZipArchiveMetadataExtractor` default) |
| `PdfMetadataExtractor` | When Apache PDFBox is on the classpath |
| `FileEncryptor` | Always (`NoOpFileEncryptor` default) |
| `QuotaChecker` | `QuotaPolicy` + `QuotaUsageProvider` (both required) |
| `FileEventPublisher` | Always (collects all `FileEventListener` beans) |
| `FileKitMetrics` | When Micrometer is on the classpath + `MeterRegistry` bean available |

## Micrometer metrics

When `spring-boot-starter-actuator` is on the classpath, file-kit automatically records metrics via `FileKitMetrics`. Bucket tags are disabled by default to prevent high-cardinality metrics; enable `file-kit.metrics-include-bucket` only when bucket names are from a small fixed set:

| Metric | Type | Description |
|--------|------|-------------|
| `file.kit.uploads` | Counter | Upload count (tags: `storageType`, `bucket`) |
| `file.kit.upload.size` | DistributionSummary | Uploaded file size in bytes |
| `file.kit.downloads` | Counter | Download count |
| `file.kit.deletes` | Counter | Delete count |
| `file.kit.copies` | Counter | Copy count |
| `file.kit.moves` | Counter | Move count |

No configuration needed — just add the actuator dependency. Exported to any configured Micrometer backend (Prometheus, Datadog, CloudWatch, etc.).

> **Cardinality warning:** metrics are tagged by `storageType` and `bucket`. Large numbers of distinct bucket names (e.g. per-user buckets) cause high cardinality. Prefer a fixed set of bucket names.

## Storage health checks

When `spring-boot-starter-actuator` is on the classpath, file-kit registers a `fileKitStorage` health indicator for `FileStorage` beans. Storages implementing the optional `StorageHealthCheck` SPI are actively probed; other storages are reported as available without an active probe. The built-in local storage checks that its base directory exists and is readable/writable. The example S3 storage uses `ListBuckets`, so its IAM role needs permission for that operation.

The library does not automatically delete orphaned objects. Safe cleanup requires a repository lifecycle/status contract and a storage listing contract, which are intentionally left to an application-specific maintenance job.

## Security Considerations

### Input validation

All domain records (`FileFormat`, `FileLocation`, `FileMetadata`) and command objects (`FileUploadCommand`) validate their constructor parameters. Null values for required fields are rejected immediately with `NullPointerException`, and invalid values (e.g., negative file size) throw `IllegalArgumentException`. `FileUploadCommand.originalFilename` is explicitly `@Nullable` — when null, a generated name is used during upload.

### Streaming upload

The upload service copies file content to a temporary file on disk while computing the checksum and capturing the format-detection header. It then streams from disk for virus scanning, encryption, and storage. With the built-in `Sha256ChecksumCalculator` / `MessageDigestChecksumCalculator`, memory usage stays O(buffer) for large uploads.

If you provide custom SPIs, keep their `InputStream` methods streaming as well. Convenience defaults such as `ChecksumCalculator.newComputation()`, `VirusScanner.scan(InputStream)`, `PdfMetadataExtractor.extract(InputStream)`, and `ArchiveMetadataExtractor.extract(InputStream)` may buffer the full input unless overridden.

### Filename safety

The upload service validates filenames **before** reading file content:
- Maximum length: 200 characters
- Rejected patterns: `..` (path traversal), `/` and `\` (directory separator)
- Null filenames are allowed — a generated name (`{key}.{extension}`) is used

These checks are also performed by `@ValidFile` / `@ValidMultipartFile` annotation validators. Shared logic lives in `FilenameValidator`.

### Bucket name restrictions

Bucket names are validated to contain only alphanumeric characters, dots, hyphens, and underscores (`[a-zA-Z0-9._-]+`). Shared via `BucketNameValidator` — applied in both `FileLocation` and `FileUploadCommand`.

### File size limits

Configure a maximum upload size to prevent memory exhaustion:

```yaml
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
- **Maximum total uncompressed size**: 1 GB (default)
- **Maximum entry count**: 65,535 (default)

Both limits are configurable via the constructor. Exceeding either throws `FileStorageException(ARCHIVE_PROCESSING_FAILED)` immediately, without processing remaining entries.

### Resource management

All internal streams and temporary files are properly closed/deleted on both success and error paths:
- Upload temp files are cleaned up in a `finally` block, even if callbacks or storage operations fail
- Decryption temp files are deleted if decryption fails (not just on stream close)
- `DeleteOnCloseInputStream` cleans up the temp file even if opening the stream fails in the constructor
- WebFlux `FilePartSource` cleans up temp files on `transferTo()` or `Files.size()` failure via `onErrorResume`
- Range request streams are closed if byte seeking fails
- Validation streams are closed after media type detection

### Thread safety

- `FileUploadService`, `FileDownloadService`, `FileDeleteService`, `FileTransferService`, and `LocalFileStorage` are thread-safe and can be used as singletons.
- `InMemoryFileStorage` is thread-safe (backed by `ConcurrentHashMap`) but is **not recommended for production** — no size limits and all data is lost on restart.

## Requirements

- Java 17+
- Jakarta Validation 3.x (for annotation-based validation)
- Spring Boot 3+ / 4+ (for the starter module)
- Apache PDFBox 3.x (optional, for PDF metadata extraction)

## License

[MIT](LICENSE)
