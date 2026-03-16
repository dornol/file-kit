# file-kit

A lightweight Java library for file validation, upload, download, and deletion. Validates uploaded files by media type, file size, filename safety, and extension-content consistency. Provides a pluggable storage abstraction for uploading and downloading files with checksum-based deduplication.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `kit-core` | `io.github.dornol:file-kit-core` | Pure Java validation, upload/download/delete logic. No framework dependency. |
| `kit-spring-boot-starter` | `io.github.dornol:file-kit-spring-boot-starter` | Spring Boot auto-configuration with `@ValidMultipartFile` and storage integration. |

## Quick Start (Spring Boot)

### 1. Add dependency

```groovy
// Gradle
implementation 'io.github.dornol:file-kit-spring-boot-starter:0.0.3'

// Optional: for better MIME detection
implementation 'org.apache.tika:tika-core:3.1.0'
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.dornol</groupId>
    <artifactId>file-kit-spring-boot-starter</artifactId>
    <version>0.0.3</version>
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

### Custom storage (S3, GCS, etc.)

Implement `FileStorage` to integrate with any storage backend. `FileUploadCommand.content()` provides an `InputStream` for streaming — no need to load the entire file into memory:

```java
public enum StorageType { S3 }

@Component
public class S3FileStorage implements FileStorage {

    private final S3Client s3;

    @Override
    public Enum<?> getStorageType() { return StorageType.S3; }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = command.key() + "." + command.extension();
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(command.bucket())
                        .key(objectKey)
                        .contentType(command.mimeType())
                        .contentLength(command.contentLength())
                        .build(),
                RequestBody.fromInputStream(command.content(), command.contentLength()));
        return new FileLocation(command.bucket(), objectKey, StorageType.S3);
    }

    @Override
    public void delete(FileMetadata metadata) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(metadata.location().bucket())
                .key(metadata.location().objectKey())
                .build());
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(metadata.location().bucket())
                .key(metadata.location().objectKey())
                .build());
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return "https://" + metadata.location().bucket()
                + ".s3.amazonaws.com/" + metadata.location().objectKey();
    }
}
```

### Multiple storage backends

Register multiple `FileStorage` beans to support different backends simultaneously. The `FileStorageResolver` routes to the correct one based on `storageType`:

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
5. Compute checksum (streaming); if a file with the same checksum already exists, return the existing metadata (deduplication)
6. Detect file format (MIME type, extension)
7. Delegate to `FileStorage.upload()` with an `InputStream` for streaming storage
8. Run callback (if provided) — on failure, delete from storage and throw
9. Save and return `FileMetadata`
10. Clean up temporary file

**Download:**
1. Look up `FileMetadata` by file key
2. Resolve the correct `FileStorage` from `metadata.location().storageType()`
3. Load and return the file content

**Delete:**
1. Look up `FileMetadata` by file key
2. Resolve the correct `FileStorage` and delete the physical file
3. Delete the metadata record

### FileResponseBuilder

Utility for building download/inline HTTP responses with proper Content-Disposition (RFC 5987, Korean filename support):

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
```

### Controller example

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

## Image Processing

file-kit provides image metadata extraction and resizing as standalone utilities. These are **not** integrated into the upload flow — use them directly where needed.

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
```

### Scale modes

| Mode | Behavior |
|------|----------|
| `FIT` | Scale to fit within target dimensions, preserving aspect ratio. Result may be smaller than target. |
| `COVER` | Scale to cover target dimensions, preserving aspect ratio. Result is cropped to exact target size. |
| `EXACT` | Scale to exact target dimensions, ignoring aspect ratio. |

### Custom implementation

Both `ImageMetadataExtractor` and `ImageResizer` are SPI interfaces with default `ImageIO`-based implementations. Register your own bean to override:

```java
@Bean
public ImageResizer imageResizer() {
    return new ThumbnailatorResizer(); // e.g., using Thumbnailator library
}
```

## Validation Checks

| Check | Description |
|-------|-------------|
| Media type | Detects actual MIME type and compares against allowed set |
| File empty | Rejects zero-byte files |
| File size | Rejects files exceeding `maxSize` (in bytes) |
| Filename | Rejects null, blank, too-long (>200 chars), and path traversal patterns (`..`, `/`, `\`) |
| Extension | Verifies that file extension matches the detected content type |

## Error Messages

### Validation messages

file-kit uses Jakarta Validation's standard message interpolation. Add a `ValidationMessages.properties` to your classpath:

```properties
file-kit.validation.unsupported-media-type=Unsupported file type
file-kit.validation.file-empty=File is empty
file-kit.validation.file-too-large=File size exceeded
file-kit.validation.invalid-filename=Invalid filename
file-kit.validation.invalid-extension=Invalid file extension
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
file-kit.image.processing-failed=Image processing failed
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

## Running the Example

The `example` module is a full Spring Boot app with JPA (PostgreSQL), S3 (MinIO), and a web UI.

```bash
# Start PostgreSQL + MinIO
cd example
docker compose up -d

# Run the app
./gradlew :example:bootRun
```

Open `http://localhost:8880` — upload files to LOCAL or S3, view the file list, download, and delete.

MinIO console: `http://localhost:9001` (minioadmin / minioadmin)

## Security Considerations

### Input validation

All domain records (`FileFormat`, `FileLocation`, `FileMetadata`) and command objects (`FileUploadCommand`) validate their constructor parameters. Null values for required fields are rejected immediately with `NullPointerException`, and invalid values (e.g., negative file size) throw `IllegalArgumentException`.

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
new FileUploadService(checksumCalculator, metadataRepository,
        formatExtractor, storageResolver, 50 * 1024 * 1024);
```

### Checksum deduplication and concurrency (TOCTOU)

The checksum-based deduplication (`findByChecksum` → `save`) is not atomic. Under concurrent uploads of the same file, duplicate entries may be stored. If strict uniqueness is required, enforce a unique constraint on the checksum column in your `FileMetadataRepository` implementation.

### Download authorization

file-kit does **not** handle download authorization. Access control (e.g., verifying that the requesting user owns the file) is the application's responsibility. Wrap `FileDownloadService` or `SpringDownloadService` calls with your own authorization logic.

### Thread safety

- `FileUploadService`, `FileDownloadService`, `FileDeleteService`, and `LocalFileStorage` are thread-safe and can be used as singletons.
- `InMemoryFileStorage` is thread-safe (backed by `ConcurrentHashMap`) but is **not recommended for production** — it has no size limits and all data is lost on restart.

## Requirements

- Java 17+
- Jakarta Validation 3.x (for annotation-based validation)
- Spring Boot 3+ / 4+ (for the starter module)

## License

[MIT](LICENSE)
