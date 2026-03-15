# file-kit

A lightweight Java library for file validation, upload, and download. Validates uploaded files by media type, file size, filename safety, and extension-content consistency. Provides a pluggable storage abstraction for uploading and downloading files with checksum-based deduplication.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `kit-core` | `io.github.dornol:file-kit-core` | Pure Java validation, upload/download logic. No framework dependency. |
| `kit-spring-boot-starter` | `io.github.dornol:file-kit-spring-boot-starter` | Spring Boot auto-configuration with `@ValidMultipartFile` and storage integration. |

## Quick Start (Spring Boot)

### 1. Add dependency

```groovy
// Gradle
implementation 'io.github.dornol:file-kit-spring-boot-starter:0.0.1'

// Optional: for better MIME detection
implementation 'org.apache.tika:tika-core:3.1.0'
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.dornol</groupId>
    <artifactId>file-kit-spring-boot-starter</artifactId>
    <version>0.0.1</version>
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

## File Storage

file-kit provides a pluggable storage abstraction. Implement the SPI interfaces and register a `FileStorage` bean, and the upload/download flow is auto-configured.

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

Implement `FileStorage` to integrate with any storage backend:

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
                        .build(),
                RequestBody.fromBytes(command.content()));
        return new FileLocation(command.bucket(), objectKey, StorageType.S3);
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

Download is automatic — `FileMetadata` records which storage was used:

```java
// Automatically reads from the correct storage
downloadService.download(fileKey);
```

This also works for scaling local storage across multiple volumes:

```java
public enum StorageType { DISK1, DISK2 }

@Bean
public FileStorage disk1() {
    return new LocalFileStorage(Path.of("/data"), StorageType.DISK1);
}

@Bean
public FileStorage disk2() {
    return new LocalFileStorage(Path.of("/data2"), StorageType.DISK2);
}
```

### Transactional upload with callback

Run business logic after upload — if it fails, the file is automatically deleted:

```java
uploadService.upload(file, StorageType.LOCAL, "uploads", metadata -> {
    businessService.process(metadata);  // throws → file rolled back
});
```

### Upload / download flow

**Upload:**
1. Read file bytes, compute checksum
2. If a file with the same checksum already exists, return the existing metadata (deduplication)
3. Detect file format (MIME type, extension)
4. Delegate to `FileStorage.upload()` for physical storage
5. Run callback (if provided) — on failure, delete from storage and throw
6. Save and return `FileMetadata`

**Download:**
1. Look up `FileMetadata` by file key
2. Resolve the correct `FileStorage` from `metadata.location().storageType()`
3. Load and return the file content

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
}
```

### Auto-configured beans

The following beans are registered automatically when their dependencies are present:

| Bean | Condition |
|------|-----------|
| `ChecksumCalculator` | Always (SHA-256 default, overridable) |
| `FileStorageResolver` | At least one `FileStorage` bean |
| `FileUploadService` | `FileMetadataRepository` + `FileFormatExtractor` + `FileStorageResolver` |
| `FileDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |
| `SpringDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |

## Validation Checks

| Check | Description |
|-------|-------------|
| Media type | Detects actual MIME type and compares against allowed set |
| File empty | Rejects zero-byte files |
| File size | Rejects files exceeding `maxSize` (in bytes) |
| Filename | Rejects null, blank, too-long (>200 chars), and path traversal patterns |
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

Open `http://localhost:8880` — upload files to LOCAL or S3, view the file list, and download.

MinIO console: `http://localhost:9001` (minioadmin / minioadmin)

## Requirements

- Java 17+
- Jakarta Validation 3.x (for annotation-based validation)
- Spring Boot 3+ / 4+ (for the starter module)

## License

[MIT](LICENSE)
